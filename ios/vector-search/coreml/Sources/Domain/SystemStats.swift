import Foundation

/// Device + power snapshot for the statistics report.
struct DeviceSnapshot: Equatable {
    let manufacturer: String
    let model: String
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
        DeviceSnapshot(manufacturer: "unknown", model: "unknown", onBattery: true, batteryPercent: nil)
    }
}
