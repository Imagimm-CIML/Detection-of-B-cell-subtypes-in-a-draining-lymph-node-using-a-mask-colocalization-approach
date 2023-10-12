import qupath.lib.analysis.DistanceTools
import qupath.lib.objects.PathObject

import static qupath.lib.scripting.QP.getAnnotationObjects
import static qupath.lib.scripting.QP.getCurrentServer

def pixel_width = getCurrentServer().getPixelCalibration().getPixelWidth()
def pixel_height = getCurrentServer().getPixelCalibration().getPixelHeight()

def annotGC = []
def annotPerif = []
def annotMed = []
def targetObjects = getAnnotationObjects()
for (ann in targetObjects) {
    if (ann.getName().startsWith("GC_0") || ann.getName().startsWith("GC_1")) {
        annotGC << ann
    }
    if (ann.getName().startsWith("GC_Periph")) {
        annotPerif << ann
    }
    if (ann.getName().startsWith("Medulla_")) {
        annotMed << ann
    }
}

// TEST ONLY
//clearDetectionMeasurements()

// medulla to gc
for (med in annotMed) {
    for (gc in annotGC) {
        print "Computing distance between " + med.getName() + " and " + gc.getName()
        distanceDetectionsToAnnotation(med, gc, pixel_width, pixel_height, "DistMedTo")
    }
}

// gc perif to gc
for (per in annotPerif) {
    for (gc in annotGC) {
        print "Computing distance between " + per.getName() + " and " + gc.getName()
        distanceDetectionsToAnnotation(per, gc, pixel_width, pixel_height, "DistPerTo")
    }
}

// min dist medulla to gc
for (med in annotMed) {
    minDist(med, "DistMedTo")
}

// min dist periph to gc
for (per in annotPerif) {
    minDist(per, "DistPerTo")
}

print "DONE!"

def distanceDetectionsToAnnotation(PathObject detectionAnnotation, PathObject annotation, pixel_width, pixel_height, measure) {
    def targetObjects = []
    targetObjects << annotation
    def sourceObjects = detectionAnnotation.getChildObjects()
    def measure_name = measure + "_" + annotation.getName()
    DistanceTools.centroidToBoundsDistance2D(sourceObjects, targetObjects, pixel_width as double, pixel_height as double, measure_name, true)
}

def minDist(PathObject detectionAnnotation, measure) {
    detectionAnnotation.getChildObjects().forEach {
        def min = Double.MAX_VALUE
        def measures = it.getMeasurements()
        def mes = measures.keySet()
        for (m in mes) {
            min = Math.min(min, measures.get(m))
        }
        it.measurementList.put(measure, min)
    }
}




