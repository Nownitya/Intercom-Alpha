import SwiftUI
import WidgetKit
import ActivityKit

/**
 * 100% Native SwiftUI Widget for iOS 18+ Live Activity and Dynamic Island.
 * Adheres strictly to Rule 1: Zero shared Compose on iOS.
 */
struct IntercomActivityWidget: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: IntercomActivityAttributes.self) { context in
            // Lock Screen / StandBy / Banner Presentation
            IntercomLockScreenBannerView(
                attributes: context.attributes,
                state: context.state
            )
            .activityBackgroundTint(Color.black.opacity(0.85))
        } dynamicIsland: { context in
            DynamicIsland {
                // Expanded Dynamic Island Presentation
                DynamicIslandExpandedRegion(.leading) {
                    HStack(spacing: 6) {
                        Image(systemName: context.state.isTransmitting ? "mic.fill" : "antenna.radiowaves.left.and.right")
                            .foregroundColor(context.state.isEmergencyActive ? .red : (context.state.isTransmitting ? .orange : .green))
                            .font(.system(size: 14, weight: .bold))

                        VStack(alignment: .leading, spacing: 2) {
                            Text("CH \(String(format: "%02d", context.state.activeChannelId))")
                                .font(.system(size: 13, weight: .black, design: .monospaced))
                                .foregroundColor(.white)
                            Text(context.state.activeChannelName)
                                .font(.system(size: 9, weight: .medium))
                                .foregroundColor(.gray)
                                .lineLimit(1)
                        }
                    }
                    .padding(.leading, 8)
                }

                DynamicIslandExpandedRegion(.trailing) {
                    VStack(alignment: .trailing, spacing: 2) {
                        if let dist = context.state.leadDistanceMeters {
                            HStack(spacing: 4) {
                                Text("\(Int(dist))m")
                                    .font(.system(size: 13, weight: .bold, design: .monospaced))
                                    .foregroundColor(.white)
                                Image(systemName: bearingArrow(context.state.relativeBearingDegrees ?? 0))
                                    .font(.system(size: 11, weight: .bold))
                                    .foregroundColor(.cyan)
                            }
                        } else {
                            HStack(spacing: 3) {
                                Image(systemName: "person.2.fill")
                                    .font(.system(size: 11))
                                    .foregroundColor(.gray)
                                Text("\(context.state.peerCount)")
                                    .font(.system(size: 12, weight: .bold, design: .monospaced))
                                    .foregroundColor(.white)
                            }
                        }

                        HStack(spacing: 3) {
                            Image(systemName: "battery.100")
                                .font(.system(size: 9))
                                .foregroundColor(context.state.batteryPercent > 20 ? .green : .red)
                            Text("\(context.state.batteryPercent)%")
                                .font(.system(size: 9, weight: .medium, design: .monospaced))
                                .foregroundColor(.gray)
                        }
                    }
                    .padding(.trailing, 8)
                }

                DynamicIslandExpandedRegion(.center) {
                    VStack(spacing: 2) {
                        if context.state.isEmergencyActive {
                            HStack(spacing: 4) {
                                Image(systemName: "exclamationmark.triangle.fill")
                                    .foregroundColor(.red)
                                Text("EMERGENCY OVERRIDE")
                                    .font(.system(size: 11, weight: .black))
                                    .foregroundColor(.red)
                            }
                        } else if context.state.isTransmitting {
                            Text("TRANSMITTING")
                                .font(.system(size: 11, weight: .black))
                                .foregroundColor(.orange)
                        } else if let speaker = context.state.activeSpeaker {
                            HStack(spacing: 4) {
                                Image(systemName: "speaker.wave.2.fill")
                                    .foregroundColor(.green)
                                    .font(.system(size: 10))
                                Text(speaker)
                                    .font(.system(size: 11, weight: .bold))
                                    .foregroundColor(.green)
                                    .lineLimit(1)
                            }
                        } else {
                            Text(context.attributes.groupName)
                                .font(.system(size: 11, weight: .semibold))
                                .foregroundColor(.gray)
                                .lineLimit(1)
                        }
                    }
                }

                DynamicIslandExpandedRegion(.bottom) {
                    HStack(spacing: 12) {
                        // Quick Status / Touch Indicator
                        HStack(spacing: 8) {
                            Circle()
                                .fill(context.state.isEmergencyActive ? Color.red : (context.state.isTransmitting ? Color.orange : Color.green))
                                .frame(width: 8, height: 8)

                            Text(context.state.isTransmitting ? "PTT Mic Open (Talking)" : "Listening • Channel Active")
                                .font(.system(size: 12, weight: .medium))
                                .foregroundColor(.white)
                        }

                        Spacer()

                        if context.state.unreadMessageCount > 0 {
                            HStack(spacing: 4) {
                                Image(systemName: "message.fill")
                                    .font(.system(size: 10))
                                    .foregroundColor(.yellow)
                                Text("\(context.state.unreadMessageCount)")
                                    .font(.system(size: 11, weight: .bold))
                                    .foregroundColor(.yellow)
                            }
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(Color.yellow.opacity(0.2))
                            .cornerRadius(6)
                        }
                    }
                    .padding(.horizontal, 10)
                    .padding(.top, 4)
                }
            } compactLeading: {
                HStack(spacing: 3) {
                    Image(systemName: context.state.isEmergencyActive ? "exclamationmark.triangle.fill" : (context.state.isTransmitting ? "mic.fill" : "antenna.radiowaves.left.and.right"))
                        .font(.system(size: 10, weight: .bold))
                        .foregroundColor(context.state.isEmergencyActive ? .red : (context.state.isTransmitting ? .orange : .green))
                    Text("C\(context.state.activeChannelId)")
                        .font(.system(size: 10, weight: .bold, design: .monospaced))
                        .foregroundColor(.white)
                }
                .padding(.leading, 4)
            } compactTrailing: {
                if let dist = context.state.leadDistanceMeters {
                    HStack(spacing: 2) {
                        Text("\(Int(dist))m")
                            .font(.system(size: 10, weight: .bold, design: .monospaced))
                            .foregroundColor(.cyan)
                    }
                    .padding(.trailing, 4)
                } else {
                    HStack(spacing: 2) {
                        Image(systemName: "person.2.fill")
                            .font(.system(size: 8))
                            .foregroundColor(.gray)
                        Text("\(context.state.peerCount)")
                            .font(.system(size: 10, weight: .bold, design: .monospaced))
                            .foregroundColor(.white)
                    }
                    .padding(.trailing, 4)
                }
            } minimal: {
                Image(systemName: context.state.isEmergencyActive ? "exclamationmark.triangle.fill" : (context.state.isTransmitting ? "mic.fill" : "antenna.radiowaves.left.and.right"))
                    .font(.system(size: 11, weight: .bold))
                    .foregroundColor(context.state.isEmergencyActive ? .red : (context.state.isTransmitting ? .orange : .green))
            }
        }
    }

    private func bearingArrow(_ degrees: Double) -> String {
        switch degrees {
        case -22.5...22.5: return "arrow.up"
        case 22.5...67.5: return "arrow.up.right"
        case 67.5...112.5: return "arrow.right"
        case 112.5...157.5: return "arrow.down.right"
        case -67.5 ... -22.5: return "arrow.up.left"
        case -112.5 ... -67.5: return "arrow.left"
        case -157.5 ... -112.5: return "arrow.down.left"
        default: return "arrow.down"
        }
    }
}

/**
 * Lock Screen & StandBy high-visibility banner view for motorcycle helmet mounting.
 */
struct IntercomLockScreenBannerView: View {
    let attributes: IntercomActivityAttributes
    let state: IntercomActivityAttributes.ContentState

    var body: some View {
        VStack(spacing: 8) {
            // Header: Group Name & Channel
            HStack {
                HStack(spacing: 6) {
                    Circle()
                        .fill(state.isEmergencyActive ? Color.red : (state.isTransmitting ? Color.orange : Color.green))
                        .frame(width: 8, height: 8)
                    Text(attributes.groupName)
                        .font(.system(size: 13, weight: .bold))
                        .foregroundColor(.white)
                }

                Spacer()

                Text("CH \(String(format: "%02d", state.activeChannelId)) • \(state.activeChannelName)")
                    .font(.system(size: 11, weight: .semibold, design: .monospaced))
                    .foregroundColor(.cyan)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 3)
                    .background(Color.cyan.opacity(0.15))
                    .cornerRadius(8)
            }

            // Main Status Box
            HStack(spacing: 12) {
                // Mic / State Icon
                ZStack {
                    RoundedRectangle(cornerRadius: 12)
                        .fill(state.isEmergencyActive ? Color.red.opacity(0.2) : (state.isTransmitting ? Color.orange.opacity(0.2) : Color.green.opacity(0.15)))
                        .frame(width: 44, height: 44)

                    Image(systemName: state.isEmergencyActive ? "exclamationmark.triangle.fill" : (state.isTransmitting ? "mic.fill" : "antenna.radiowaves.left.and.right"))
                        .font(.system(size: 20, weight: .bold))
                        .foregroundColor(state.isEmergencyActive ? .red : (state.isTransmitting ? .orange : .green))
                }

                VStack(alignment: .leading, spacing: 3) {
                    if state.isEmergencyActive {
                        Text("EMERGENCY SOS OVERRIDE")
                            .font(.system(size: 14, weight: .black))
                            .foregroundColor(.red)
                    } else if state.isTransmitting {
                        Text("TRANSMITTING")
                            .font(.system(size: 14, weight: .black))
                            .foregroundColor(.orange)
                    } else if let speaker = state.activeSpeaker {
                        Text("\(speaker) speaking...")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(.green)
                    } else {
                        Text("Channel Clear • Ready")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundColor(.white)
                    }

                    HStack(spacing: 8) {
                        Text("\(state.peerCount) connected riders")
                            .font(.system(size: 11))
                            .foregroundColor(.gray)

                        if let dist = state.leadDistanceMeters {
                            Text("• Lead: \(Int(dist))m")
                                .font(.system(size: 11, weight: .medium, design: .monospaced))
                                .foregroundColor(.cyan)
                        }
                    }
                }

                Spacer()

                // Battery Telemetry
                VStack(spacing: 2) {
                    Image(systemName: "battery.100")
                        .font(.system(size: 14))
                        .foregroundColor(state.batteryPercent > 20 ? .green : .red)
                    Text("\(state.batteryPercent)%")
                        .font(.system(size: 10, weight: .bold, design: .monospaced))
                        .foregroundColor(.gray)
                }
            }
        }
        .padding(14)
    }
}
