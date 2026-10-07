import Foundation

/// Device + power + hardware snapshot for the statistics report.
struct DeviceSnapshot: Equatable {
    let manufacturer: String
    let model: String
    let ramGB: Double?
    let cpuCores: Int?
    let gpuCores: Int?
    let aneCores: Int?
    let onBattery: Bool
    let batteryPercent: Int?
}

/// Source of device/power info. Faked in unit tests.
protocol SystemStatsProvider {
    func snapshot() -> DeviceSnapshot
}

/// Fallback when no platform provider is wired (unit tests).
struct UnknownSystemStats: SystemStatsProvider {
    func snapshot() -> DeviceSnapshot {
        DeviceSnapshot(
            manufacturer: "unknown", model: "unknown",
            ramGB: nil, cpuCores: nil, gpuCores: nil, aneCores: nil,
            onBattery: true, batteryPercent: nil
        )
    }
}

/// Known Apple hardware specs keyed by machine identifier (e.g. "iPhone14,6").
/// There is no public API for GPU/ANE core counts or marketing names, so this
/// small table covers devices we verified; everything else falls back to
/// measured values (RAM/CPU) and "unknown".
struct KnownDevice {
    let marketingName: String
    let gpuCores: Int
    let aneCores: Int
}

let knownDevices: [String: KnownDevice] = [
    // iPhone SE (3rd generation), A15 Bionic.
    "iPhone14,6": KnownDevice(marketingName: "iPhone SE (3rd generation)", gpuCores: 4, aneCores: 16),
]

/// Friendly device name for a machine identifier, falling back to the raw id.
func marketingName(for identifier: String) -> String {
    knownDevices[identifier]?.marketingName ?? identifier
}
