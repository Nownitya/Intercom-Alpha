import SwiftUI
import CoreImage.CIFilterBuiltins

public struct QrShareView: View {
    let groupName: String
    let inviteCode: String
    @Environment(\.dismiss) private var dismiss

    public init(groupName: String, inviteCode: String) {
        self.groupName = groupName
        self.inviteCode = inviteCode
    }

    public var body: some View {
        NavigationView {
            ZStack {
                TacticalTheme.background.ignoresSafeArea()

                VStack(spacing: 24) {
                    VStack(spacing: 6) {
                        Text("INVITE CONVOY RIDERS")
                            .font(.system(size: 13, weight: .black, design: .monospaced))
                            .foregroundColor(TacticalTheme.accent)

                        Text("Group: \(groupName)")
                            .font(.system(size: 18, weight: .bold))
                            .foregroundColor(TacticalTheme.textPrimary)
                    }
                    .padding(.top, 24)

                    if let qrImage = generateQrCode(from: inviteCode) {
                        Image(uiImage: qrImage)
                            .interpolation(.none)
                            .resizable()
                            .scaledToFit()
                            .frame(width: 220, height: 220)
                            .padding(16)
                            .background(Color.white)
                            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                    }

                    Text("Scan with another device running Intercom-Alpha to join without internet or accounts.")
                        .font(.system(size: 12))
                        .foregroundColor(TacticalTheme.textSecondary)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 32)

                    Spacer()

                    Button(action: { dismiss() }) {
                        Text("DONE")
                            .font(.system(size: 13, weight: .bold, design: .monospaced))
                            .foregroundColor(Color.black)
                            .frame(maxWidth: .infinity)
                            .frame(height: 48)
                            .background(TacticalTheme.accent)
                            .clipShape(Capsule())
                    }
                    .padding(.horizontal, 24)
                    .padding(.bottom, 16)
                }
            }
            .navigationBarHidden(true)
        }
    }

    private func generateQrCode(from string: String) -> UIImage? {
        let context = CIContext()
        let filter = CIFilter.qrCodeGenerator()
        filter.setValue(Data(string.utf8), forKey: "inputMessage")

        guard let outputImage = filter.outputImage else { return nil }
        let scaledImage = outputImage.transformed(by: CGAffineTransform(scaleX: 10, y: 10))
        guard let cgImage = context.createCGImage(scaledImage, from: scaledImage.extent) else { return nil }
        return UIImage(cgImage: cgImage)
    }
}

