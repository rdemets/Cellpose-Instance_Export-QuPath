/**
 * Cellpose Detection Template script
 * @author Olivier Burri
 *
 * This script is a template to detect objects using a Cellpose model from within QuPath.
 * After defining the builder, it will:
 * 1. Find all selected annotations in the current open ImageEntry
 * 2. Export the selected annotations to a temp folder that can be specified with tempDirectory()
 * 3. Run the cellpose detction using the defined model name or path
 * 4. Reimport the mask images into QuPath and create the desired objects with the selected statistics
 *
 * NOTE: that this template does not contain all options, but should help get you started
 * See all options in https://biop.github.io/qupath-extension-cellpose/qupath/ext/biop/cellpose/CellposeBuilder.html
 * and in https://cellpose.readthedocs.io/en/latest/command.html
 *
 * NOTE 2: You should change pathObjects get all annotations if you want to run for the project. By default this script
 * will only run on the selected annotations.
 */
 
 
import qupath.ext.biop.cellpose.Cellpose2D
createFullImageAnnotation(true)
selectAnnotations()


// Specify the model name (cyto, nuclei, cyto2, ... or a path to your custom model as a string)
// Other models for Cellpose https://cellpose.readthedocs.io/en/latest/models.html
// And for Omnipose: https://omnipose.readthedocs.io/models.html
def pathModel = 'cyto3'
def cellpose = Cellpose2D.builder( pathModel )
        .pixelSize( 0.5 )                  // Resolution for detection in um
        .channels( 'DAPI' )	               // Select detection channel(s)
//        .tempDirectory( new File( '/tmp' ) ) // Temporary directory to export images to. defaults to 'cellpose-temp' inside the QuPath Project
//        .preprocess( ImageOps.Filters.median(1) )                // List of preprocessing ImageOps to run on the images before exporting them
//        .normalizePercentilesGlobal(0.1, 99.8, 10) // Convenience global percentile normalization. arguments are percentileMin, percentileMax, dowsample.
//        .tileSize(1024)                  // If your GPU can take it, make larger tiles to process fewer of them. Useful for Omnipose
//        .cellposeChannels(1,2)           // Overwrites the logic of this plugin with these two values. These will be sent directly to --chan and --chan2
//        .cellprobThreshold(0.0)          // Threshold for the mask detection, defaults to 0.0
//        .flowThreshold(0.4)              // Threshold for the flows, defaults to 0.4
        .diameter(75)                    // Median object diameter. Set to 0.0 for the `bact_omni` model or for automatic computation
//        .useOmnipose()                   // Use omnipose instead
//        .addParameter("cluster")         // Any parameter from cellpose or omnipose not available in the builder.
//        .addParameter("save_flows")      // Any parameter from cellpose or omnipose not available in the builder.
//        .addParameter("anisotropy", "3") // Any parameter from cellpose or omnipose not available in the builder.
//        .cellExpansion(5.0)              // Approximate cells based upon nucleus expansion
//        .cellConstrainScale(1.5)         // Constrain cell expansion using nucleus size
//        .classify("My Detections")       // PathClass to give newly created objects
        .measureShape()                  // Add shape measurements
        .measureIntensity()              // Add cell measurements (in all compartments)
//        .createAnnotations()             // Make annotations instead of detections. This ignores cellExpansion
//        .simplify(0)                     // Simplification 1.6 by default, set to 0 to get the cellpose masks as precisely as possible
        .build()

// Run detection for the selected objects
def imageData = getCurrentImageData()
def pathObjects = getSelectedObjects() // To process only selected annotations, useful while testing
// def pathObjects = getAnnotationObjects() // To process all annotations. For working in batch mode
if (pathObjects.isEmpty()) {
    Dialogs.showErrorMessage("Cellpose", "Please select a parent object!")
    return
}

cellpose.detectObjects(imageData, pathObjects)

// You could do some post-processing here, e.g. to remove objects that are too small, but it is usually better to
// do this in a separate script so you can see the results before deleting anything.


println 'Cellpose detection script done'
runObjectClassifier("CK_classifier")
//selectObjectsByClassification("Ignore*");
//selectObjectsByClassification("CK (Opal 690)");
//removeSelectedObjects()



// 1 is full resolution. You may want something more like 20 or higher for small thumbnails
downsample = 1 
//remove the findAll to get all annotations, or change the null to getPathClass("Tumor") to only export Tumor annotations
annotations = getAnnotationObjects().findAll{it.getPathClass() == null}

def imageName = GeneralTools.getNameWithoutExtension(getCurrentImageData().getServer().getMetadata().getName())

//Make sure the location you want to save the files to exists - requires a Project
def pathOutput = buildFilePath(PROJECT_BASE_DIR, 'image_export')
mkdirs(pathOutput)


def cellLabelServer = new LabeledImageServer.Builder(imageData)
    .backgroundLabel(0, ColorTools.WHITE) // Specify background label (usually 0 or 255)
    //.useCells()
    .useDetections()
    .useInstanceLabels()
    .downsample(downsample)    // Choose server resolution; this should match the resolution at which tiles are exported    
    .multichannelOutput(false) // If true, each label refers to the channel of a multichannel binary image (required for multiclass probability)
    .build()
//def annotationLabelServer = new LabeledImageServer.Builder(imageData)
//    .backgroundLabel(0, ColorTools.WHITE) // Specify background label (usually 0 or 255)
//    .downsample(downsample)    // Choose server resolution; this should match the resolution at which tiles are exported    
//    .multichannelOutput(false) // If true, each label refers to the channel of a multichannel binary image (required for multiclass probability)
//    .build()




annotations.eachWithIndex{anno,x->
    roi = anno.getROI()
    def requestROI = RegionRequest.createInstance(getCurrentServer().getPath(), 1, roi)
   
    pathOutput = buildFilePath(PROJECT_BASE_DIR, 'image_export', imageName)
    //Now to export one image of each type per annotation (in the default case, unclassified
    
    //objects with overlays as seen in the Viewer    
    //writeRenderedImageRegion(getCurrentViewer(), requestROI, pathOutput+"_rendered.tif")
    //Labeled images, either cells or annotations
    //writeImageRegion(annotationLabelServer, requestROI, pathOutput+"_annotationLabels.tif")
    writeImageRegion(cellLabelServer, requestROI, pathOutput+".tif")
    
    //To get the image behind the objects, you would simply use writeImageRegion
    //writeImageRegion(getCurrentServer(), requestROI, pathOutput+"_original.tif")

} 


println 'Done, image saved in '+ pathOutput
