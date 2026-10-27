// swift-tools-version:5.9
import PackageDescription

/// LiteRT 2.3 iOS binaries (C API + Metal accelerator), referenced by release asset so no
/// binary is committed. The 2.17 CocoaPods pod is the older runtime kept for comparison.
let package = Package(
    name: "LiteRTBinary",
    platforms: [.iOS(.v15)],
    products: [
        .library(name: "CLiteRT", targets: ["CLiteRT"]),
        .library(name: "LiteRtMetalAccelerator", targets: ["LiteRtMetalAccelerator"]),
    ],
    targets: [
        .binaryTarget(
            name: "CLiteRT",
            url: "https://github.com/google-ai-edge/LiteRT/releases/download/v2.3.0/CLiteRT.xcframework.zip",
            checksum: "b36a1a4f1b3eeb04546b4cc2986902cd704439b37c34597ab0f804aed08f5a00"
        ),
        .binaryTarget(
            name: "LiteRtMetalAccelerator",
            url: "https://github.com/google-ai-edge/LiteRT/releases/download/v2.3.0/LiteRtMetalAccelerator.xcframework.zip",
            checksum: "e564e6678959098a35c1dbcffc60041afac25a845abcee998a53f958e342aeec"
        ),
    ]
)
