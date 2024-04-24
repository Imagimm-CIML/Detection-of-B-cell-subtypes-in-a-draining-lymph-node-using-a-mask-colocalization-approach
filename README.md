# Detection of B cell subtypes in a draining lymph node using a mask colocalization approach 

### Image of Lymph node 


| Channel |legend |
|-----:|-----------|
|     1| eYFP   Cells recently coming from the Germinal Center  |
|     2| CD138 – Antibody secreting cells                       |
|     3| IRF4 – Antibody secreting cells                        |
|     4| Bcl6 – Germinal Center B cells                   |

## Quantification of each cell populations in each annotated area (GC, GC periph, medulla) :  
The script called Cellpose2coloc segment the cells for each channel using CellPose extension for QuPath (https://github.com/BIOP/qupath-extension-cellpose). 

Then it analyzes the colocalization of the different masks created in each channel and compute the colocalisation of the different masks. 
An example with two channels :  
![coloc](https://github.com/matfallet/Detection-of-B-cell-subtypes-in-a-draining-lymph-node-using-a-mask-colocalization-approach-/assets/41480459/f56cd42d-e68c-41fe-85e3-502a2b67844f)

Depending on the colocalisation combination a number is computed for each colocalisation combination:

1) mask YFP = 1  
2) mask IRF4 = 2   
3) mask Bcl6 = 4  
4) mask CD138 = 8     
5) mask YFP + IRF4 = 1 + 4 = 5  
6) mask YFP + Bcl6 = 1 + 8 = 9  
7) mask YFP + CD138  = 1 + 2 = 3  
8) mask YFP + CD138 + IRF4 = 1 + 2 + 4 = 7  
9) mask YFP + Bcl6 + IRF4  = 1 + 8 + 4 = 13  
10) mask YFP + Bcl6 + IRf + CD138 = 1 + 2 + 4 + 8 = 15  
11) mask  IRF4 + mask  Bcl6  = 4 + 8 = 12  
12) mask  CD138 + mask IRF4 + mask Bcl6  = 2 + 4 + 8 = 14

The colocalisation is baseb on the **multicoloc** plugin from the 3D ImageJ Suite (https://mcib3d.frama.io/3d-suite-imagej/). 


# Steps to perform : install cellpose in QuPath before and use GPU   

1) Open Qupath, and create a project in the same directory as the images to analyze (or put the images after creating the project)
2) Drag and drop the three extension jar (mcibd_dev.jar, mcibd-core-4.1.0 and qupath-extension-cellpose-0.6.1) in Qupath  
3) Drag the image to analyze in Qupath 
4) Annotate the different areas in QuPath : _image>annotation>Create annotation_  
    Here, we annotated the whole tissue, the germinal centers (GC) , the medulla, and the GC-Medulla Interface (GMI)

![image](https://github.com/Imagimm-CIML/Detection-of-B-cell-subtypes-in-a-draining-lymph-node-using-a-mask-colocalization-approach/assets/124134416/a14407de-ad9b-4b6c-a936-aed1ee663eed)



6) Give a name for each channel (here YFP, IRF4, Bcl6, CD138)

![image](https://github.com/Imagimm-CIML/Detection-of-B-cell-subtypes-in-a-draining-lymph-node-using-a-mask-colocalization-approach/assets/124134416/7df5a7e4-c224-4f36-87e1-fabc26ecbca0)


8) Drag and drop the script _Cellpose2coloc.groovy_
9) Change the thresholds for the different channels in the script (cells with average intensity below threshold will be discarded) and adjust the channel names.
   For that you can launch the script a first time with very low threshold values and use _create single measure classifier_ (histogram and live) to adjust the threshold interactively and fix them in the script

![image](https://github.com/Imagimm-CIML/Detection-of-B-cell-subtypes-in-a-draining-lymph-node-using-a-mask-colocalization-approach/assets/124134416/3619dd77-15ec-4c62-88a0-ec3c6131a59c)
![image](https://github.com/Imagimm-CIML/Detection-of-B-cell-subtypes-in-a-draining-lymph-node-using-a-mask-colocalization-approach/assets/124134416/ae2b7f9f-ea11-4f75-932a-131d4326a684)


11) Launch the script 
12) You can then export the final measurements as a CSV file that will contain the columns named "LabelColoc"(teh cell label in the merged image), "Nbcoloc" (number of colocalised mask for the cell), "OrderColoc"(the number representing the combination of colocalisation masks from 1 to 15)

![image](https://github.com/Imagimm-CIML/Detection-of-B-cell-subtypes-in-a-draining-lymph-node-using-a-mask-colocalization-approach/assets/124134416/77e77ef4-42f9-451a-a5ef-afcbcd7896be)

    
_Optional : You can also calculate the minimal distance to GC of all detections by launching the script "distances.groovy"_ 

#LICENCE GPL v3.0    
