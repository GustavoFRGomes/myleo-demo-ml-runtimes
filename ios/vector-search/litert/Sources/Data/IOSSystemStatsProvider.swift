import Foundation
import UIKit

/// `SystemStatsProvider` reading `utsname` + `UIDevice` battery (no permissions needed).
/// `batteryPercent` is nil on simulator / when monitoring is unavailable.
struct IOSSystemStatsProvider: SystemStatsProvider {
    func snapshot() -> DeviceSnapshot {
        var systemInfo = utsname()
        uname(&systemInfo)
        let model = withUnsafePointer(to: &systemInfo.machine) {
            $0.withMemoryRebound(to: CChar.self, capacity: 1) {
                String(validatingCString: $0) ?? UIDevice.current.model
            }
        }
        let device = UIDevice.current
        device.isBatteryMonitoringEnabled = true
        let percent: Int? = device.batteryLevel >= 0 ? Int((device.batteryLevel * 100).rounded()) : nil
        let onBattery: Bool
        switch device.batteryState {
        case .charging, .full: onBattery = false
        case .unplugged, .unknown: onBattery = true
        @unknown default: onBattery = true
        }
        return DeviceSnapshot(manufacturer: "Apple", model: model, onBattery: onBattery, batteryPercent: percent)
    }
}
