// Bridging header: the ONNX Runtime ObjC API (used by the vector-search leaf) and
// the C API, which is the only surface that can express the graph's bool `marker_mask`
// tensor (the ObjC wrapper has no bool element type).
#import <onnxruntime.h>
#import <onnxruntime_c_api.h>
