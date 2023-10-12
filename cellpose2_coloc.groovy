import ij.IJ
import ij.ImagePlus
import mcib3d.geom2.Object3DInt
import mcib3d.geom2.Objects3DIntPopulation
import mcib3d.geom2.measurements.MeasureIntensity
import mcib3d.geom2.measurements.MeasureObject
import mcib3d.image3d.ImageHandler
import mcib3d.image3d.ImageShort
import mcib3d.image3d.processing.ImageStitcher
import mcib_dev.Multiplex
import qupath.ext.biop.cellpose.Cellpose2D
import qupath.imagej.processing.RoiLabeling
import qupath.imagej.tools.IJTools
import qupath.lib.analysis.features.ObjectMeasurements
import qupath.lib.geom.ImmutableDimension
import qupath.lib.gui.dialogs.Dialogs
import qupath.lib.objects.PathObject
import qupath.lib.objects.PathObjects
import qupath.lib.regions.ImagePlane
import qupath.lib.roi.RoiTools
import qupath.lib.roi.interfaces.ROI
import qupath.opencv.ops.ImageOps

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

import static qupath.lib.gui.scripting.QPEx.saveDetectionMeasurements
import static qupath.lib.scripting.QP.*

// global variables
def cellposePath = getProject().path.getParent().toString() + File.separator + "cellpose-temp"
def segPath = getProject().path.getParent().toString() + File.separator + "masks"
def file = new File(segPath)
if (!file.exists()) file.mkdir()
print "MASKS " + file.getPath()
// reset detections and measurements
clearDetectionMeasurements()
clearDetections()

// multiplex for all regions and selected channels
def imageData = getCurrentImageData()
def hierarchy = imageData.getHierarchy()
def annotations = hierarchy.getAnnotationObjects()
def channelsToProcess = [0, 1, 2, 3] // the different channels to segment
def models = ["cyto2", "cyto2", "cyto2", "cyto2"] // the model used for cellpose for the different channels
def diameters = [15, 15, 15, 15] // the diameters used for cellpose for the different channels
def flows = [.5, .5, .5, .5] // the flow threshod value used for cellpose for the different channels
def cellProbs = [.6, .6, .6, .3] // the cell probability value used for cellpose for the different channels
def thresholds = ["A488": 10931, "PE-561": 12000, "A594": 7790, "A647": 10218] // cells having average intensity below threshold will be discarded

annotations.forEach {
    // uncomment next line if you want to test for a specific annotation
    //if (it.getName().equalsIgnoreCase("Medulla_01")) {
        print "Processing " + it.getName()
        cellposeChannels(it, channelsToProcess, thresholds, models, diameters, flows, cellProbs, cellposePath, segPath)
        // cell detections
        multiplex(it, channelsToProcess, segPath) // do the colocalistion procedure
        measureMultiplex(it, segPath)  // measure the detected cells
    //}
}

print "DONE"

saveDetectionMeasurements(imageData, getProject().getPath().getParent().toString())

print "All done !"

///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
def cellposeChannels(PathObject regionObject, List<Integer> channelsToProcess, Map<String, Integer> thresholds, List<String> models, List<Double> diameters, List<Double> flows, List<Double> cellProbs, String cellposePath, String segPath) {
    def imageData = getCurrentImageData()
    def server = imageData.getServer()
    for (c in channelsToProcess) { // will process selected region
        print "Processing channel " + server.getChannel(c).name
        runCellpose(regionObject, server.getChannel(c).name, models[c], thresholds[server.getChannel(c).name] as float, diameters[c], flows[c], cellProbs[c], cellposePath, segPath)
    }
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
def runCellpose(PathObject regionObject, String channelName, String modelName, float threshold, double diameter, double flowThreshold, double cellProb, String cellposePath, String segPath) {
    def imageData = getCurrentImageData()
    // Specify the model name (cyto, nuc, cyto2, omni_bact or a path to your custom model)
    def pathModel = modelName
    def cellpose = Cellpose2D.builder(pathModel)
//        .pixelSize( 0.5 )             // Resolution for detection in um
            .channels(channelName)        // Select detection channel(s)
            .preprocess(ImageOps.Filters.median(1))                // List of preprocessing ImageOps to run on the images before exporting them
//        .normalizePercentilesGlobal(0.1, 99.8, 10) // Convenience global percentile normalization. arguments are percentileMin, percentileMax, dowsample.
            .tileSize(4096)                  // If your GPU can take it, make larger tiles to process fewer of them. Useful for Omnipose
//        .cellposeChannels(1,2)         // Overwrites the logic of this plugin with these two values. These will be sent directly to --chan and --chan2
            .cellprobThreshold(cellProb)        // Threshold for the mask detection, defaults to 0.0
            .flowThreshold(flowThreshold)            // Threshold for the flows, defaults to 0.4
            .diameter(diameter)                    // Median object diameter. Set to 0.0 for the `bact_omni` model or for automatic computation
//        .addParameter("save_flows")      // Any parameter from cellpose not available in the builder. See https://cellpose.readthedocs.io/en/latest/command.html
//        .addParameter("anisotropy", "3") // Any parameter from cellpose not available in the builder. See https://cellpose.readthedocs.io/en/latest/command.html
//        .cellExpansion(5.0)              // Approximate cells based upon nucleus expansion
//        .cellConstrainScale(1.5)       // Constrain cell expansion using nucleus size
    //.classify("Detect_" + channelName)     // PathClass to give newly created objects
            .measureShape()                // Add shape measurements
            .measureIntensity()             // Add cell measurements (in all compartments)
//        .createAnnotations()           // Make annotations instead of detections. This ignores cellExpansion
            .simplify(0)                   // Simplification 1.6 by default, set to 0 to get the cellpose masks as precisely as possible
            .build()

// Run detection for the selected objects
    def pathObjects = new LinkedList()
    pathObjects.add(regionObject)
    print "Annotation " + pathObjects
    if (pathObjects.isEmpty()) {
        Dialogs.showErrorMessage("Cellpose", "Please select a parent object!")
        return
    }
    cellpose.detectObjects(imageData, pathObjects) // all detections before thresholding
    println 'Done Cellpose'
// cp results to masks after thresholding
    thresholdCellsIntensity(threshold, cellposePath)
    // merge tiles (if any) before save
    def tileWidth = 4096
    def finalDownsample = 1
    def tileHeight = 4096
    def overlap = 2 * diameter

    Collection<? extends ROI> rois = RoiTools.computeTiledROIs(regionObject.getROI(), ImmutableDimension.getInstance(tileWidth * finalDownsample, tileWidth * finalDownsample), ImmutableDimension.getInstance(tileWidth * finalDownsample, tileHeight * finalDownsample), true, overlap * finalDownsample as int);
    if (rois.size() > 1) { // tiles, need stitching
        def ima = stitchImages(cellposePath)
        ima.setTitle(regionObject.name + "_" + channelName + ".tif")
        ima.save(segPath)
    } else { // no tile, only one image
        Files.list(Path.of(cellposePath)).filter { it.toString().endsWith("_cp_masks.tif") }
                .forEach { Files.copy(it, Path.of(segPath, regionObject.name + "_" + channelName + ".tif"), StandardCopyOption.REPLACE_EXISTING) }
    }
}

/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
def stitchImages(String cellposePath) {
    List<ImageHandler> images = new LinkedList<>()
    int sx = 0
    int sy = 0
    int minX = Integer.MAX_VALUE
    int minY = Integer.MAX_VALUE

    Files.list(Path.of(cellposePath)).filter { it.toString().endsWith("_cp_masks.tif") }
            .forEach {
                def path = it.toString()
                print "stitching " + it
                def ima = ImageHandler.wrap(IJ.openImage(path))
                def pos = path.indexOf("Temp_")
                def pos1 = path.indexOf("_", pos + 6)
                def pos2 = path.indexOf("_", pos1 + 1)
                def offX = path.substring(pos + 5, pos1) as int
                def offY = path.substring(pos1 + 1, pos2) as int
                ima.setOffset(offX, offY, 0)
                images.add(ima)
                // size maxima
                sx = Math.max(sx, ima.sizeX + offX)
                sy = Math.max(sy, ima.sizeX + offY)
                minX = Math.min(minX, offX)
                minY = Math.min(minY, offY)
            }
    print("start " + minX + " " + minY)
    print("size max " + sx + " " + sy)
    ImageHandler draw = new ImageShort("stitch", sx - minX, sy - minY, 1)
    def stitcher = new ImageStitcher(images)
    stitcher.setMergeMethod(1) // Union
    def pop = stitcher.stitchLabelImages()
    pop.translateObjects(-minX, -minY, 0)
    pop.drawInImage(draw)

    return draw
}

///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
def multiplex(PathObject regionObject, List<Integer> channelsToProcess, String segPath) {
    def tileWidth = 4096
    def tileHeight = 4096
    def overlap = 0
    def finalDownsample = 1


    def regionName = regionObject.getName()
    def imageData = getCurrentImageData()
    def server = imageData.getServer()
// merge channels
    def segs = new LinkedList<ImageHandler>()
    for (int c in channelsToProcess) {
        def imaPath = Path.of(segPath, regionName + "_" + server.getChannel(c).name + ".tif").toString()
        print "Opening" + imaPath
        def plus = IJ.openImage(imaPath)
        def handler = ImageHandler.wrap(plus)
        segs.add(handler)
    }
    def merge = segs[0].createSameDimensions()
    new ImageStitcher(segs).stitchLabelImages(0.5).drawInImage(merge)
    merge.setTitle(regionName + "_merged")
    merge.save(segPath, true)

    def multi = new Multiplex(segs, merge)
    def res = multi.multiplex()
    res[0].setTitle(regionName + "_Nb")
    res[0].save(segPath, true)
    res[1].setTitle(regionName + "_Order")
    res[1].save(segPath, true)
}

/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
def measureMultiplex(PathObject regionObject, String segPath) {
    def imageData = getCurrentImageData()
    def server = imageData.getServer()
    def regionName = regionObject.getName()
    def imaPath = Path.of(segPath, regionName + "_Nb.tif").toString()
    def plusNbColoc = IJ.openImage(imaPath)
    def ipNbColoc = plusNbColoc.getProcessor()
    imaPath = Path.of(segPath, regionName + "_Order.tif").toString()
    def plusOrder = IJ.openImage(imaPath)
    def ipOrder = plusOrder.getProcessor()
    imaPath = Path.of(segPath, regionName + "_merged.tif").toString()
    def plusMerge = IJ.openImage(imaPath)
    def ipMerge = plusMerge.getProcessor()

    // delete detections not validated by thresholding, labelColoc
    regionObject.clearChildObjects()

    ImagePlane plane = ImagePlane.getDefaultPlane()
    int n = plusMerge.getStatistics().max as int
    def roisIJ = RoiLabeling.labelsToConnectedROIs(ipMerge, n)
    def rois = roisIJ.collect {
        if (it == null) return
        def roi2 = IJTools.convertToROI(it, 0, 0, 1, plane)
        // translate to put in full image
        roi2 = roi2.translate(regionObject.getROI().boundsX, regionObject.getROI().boundsY)
        return roi2
    }
    rois = rois.findAll { null != it }.findAll { regionObject.getROI().contains(it.centroidX, it.centroidY) }

// ADD ROI AS DETECTION and add measurements
    def pathObjects = rois.collect {
        def detect = PathObjects.createDetectionObject(it)
        def point = detect.getROI().getAllPoints()[0]
        def xx = (int) Math.round(point.x - regionObject.getROI().boundsX)
        def yy = (int) Math.round(point.y - regionObject.getROI().boundsY)
        detect.measurementList.put("LabelColoc", ipMerge.getPixelValue(xx, yy))
        detect.measurementList.put("NbColoc", ipNbColoc.getPixelValue(xx, yy))
        detect.measurementList.put("OrderColoc", ipOrder.getPixelValue(xx, yy))

        return detect
    }

    Collection<ObjectMeasurements.Measurements> measurements = new LinkedList<>()
    Collection<ObjectMeasurements.Compartments> compartments = new LinkedList<>()
    compartments.add(ObjectMeasurements.Compartments.NUCLEUS)
    measurements.add(ObjectMeasurements.Measurements.MEAN)
    measurements.add(ObjectMeasurements.Measurements.MIN)
    measurements.add(ObjectMeasurements.Measurements.MAX)


    pathObjects.forEach { ObjectMeasurements.addIntensityMeasurements(server, it, 1, measurements, compartments) }
    def cal = server.getMetadata().getPixelCalibration()
    pathObjects.forEach { ObjectMeasurements.addShapeMeasurements(it, cal, ObjectMeasurements.ShapeFeatures.AREA, ObjectMeasurements.ShapeFeatures.CIRCULARITY) }
    regionObject.addChildObjects(pathObjects)
}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
def thresholdCellsIntensity(Float threshold, String cellposePath) {
    def imageData = getCurrentImageData()
    def server = imageData.getServer()
    // get images
    def imaPath = Path.of(cellposePath)
    Files.list(imaPath).filter { it.toString().endsWith("_cp_masks.tif") }.forEach {
        def maskFile = it.toString()
        def signalFile = maskFile.replace("_cp_masks.tif", ".tif")
        print maskFile
        print signalFile
        // get population
        ImagePlus maskPlus = IJ.openImage(maskFile)
        ImagePlus signalPlus = IJ.openImage(signalFile)
        ImageHandler mask = ImageHandler.wrap(maskPlus)
        ImageHandler signal = ImageHandler.wrap(signalPlus)
        def population = new Objects3DIntPopulation(mask)
        List<Object3DInt> cellsOK = population.getObjects3DInt().findAll { new MeasureObject(it).measureIntensity(MeasureIntensity.INTENSITY_AVG, signal) >= threshold }
        population = new Objects3DIntPopulation()
        population.addObjects(cellsOK)
        population.resetLabels()
        mask.fill(0)
        population.drawInImage(mask)
        mask.setTitle(maskPlus.getTitle())
        mask.save(cellposePath)
    }
}
