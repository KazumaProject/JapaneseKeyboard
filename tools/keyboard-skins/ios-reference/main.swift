import UIKit
import CoreText

// Uses the system keyboard; no custom inputView, appearance proxy, or private API.
final class ReferenceController: UIViewController {
    private let input = UITextField()

    override func viewDidLoad() {
        super.viewDidLoad()
        if ProcessInfo.processInfo.arguments.contains("--font-specimens") { exportFontSpecimens() }
        view.backgroundColor = ProcessInfo.processInfo.arguments.contains("--motion-contrast") ? UIColor(white: 0.5, alpha: 1) : .systemBackground
        overrideUserInterfaceStyle = ProcessInfo.processInfo.arguments.contains("--dark") ? .dark : .light

        let appearance = UISegmentedControl(items: ["Light", "Dark"])
        appearance.selectedSegmentIndex = ProcessInfo.processInfo.arguments.contains("--dark") ? 1 : 0
        appearance.addTarget(self, action: #selector(changeAppearance(_:)), for: .valueChanged)
        appearance.accessibilityIdentifier = "reference.appearance"

        input.borderStyle = .roundedRect
        input.placeholder = "System keyboard reference"
        input.autocorrectionType = .default
        input.spellCheckingType = .default
        input.autocapitalizationType = .sentences
        // Keep standard keyboard traits. Seed text makes English preview case reproducible.
        if ProcessInfo.processInfo.arguments.contains("--controlled") { input.text = "a" }
        input.keyboardType = .default
        input.returnKeyType = .default
        input.accessibilityIdentifier = "reference.input"

        let instructions = UILabel()
        instructions.text = "iOS keyboard reference\nSelect English (US) or Japanese Kana using the globe key."
        instructions.numberOfLines = 0
        let stack = UIStackView(arrangedSubviews: [instructions, appearance, input])
        stack.axis = .vertical
        stack.spacing = 16
        stack.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(stack)
        if ProcessInfo.processInfo.arguments.contains("--fidelity-clock") {
            let clock = FidelityClockView(frame: CGRect(x: 20, y: 320, width: 320, height: 30))
            clock.isUserInteractionEnabled = false
            clock.accessibilityIdentifier = "reference.clock"
            view.addSubview(clock)
        }
        NSLayoutConstraint.activate([
            stack.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 24),
            stack.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor, constant: 20),
            stack.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor, constant: -20),
            input.heightAnchor.constraint(equalToConstant: 44)
        ])
    }

    private func exportFontSpecimens() {
        let output = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0].appendingPathComponent("font-specimens")
        try? FileManager.default.createDirectory(at: output, withIntermediateDirectories: true)
        var metadata: [[String: Any]] = []
        for text in ["あ", "か", "に", "ぬ", "ね", "の", "q", "e", "p", "a", "l"] {
            for halfPoints in 40...84 {
                let size = CGFloat(halfPoints) / 2
                let font = UIFont.systemFont(ofSize: size)
                let attributed = NSAttributedString(string: text, attributes: [.font: font, .foregroundColor: UIColor.black])
                let line = CTLineCreateWithAttributedString(attributed)
                let format = UIGraphicsImageRendererFormat()
                format.scale = 3
                let png = UIGraphicsImageRenderer(size: CGSize(width: 120, height: 100), format: format).pngData { renderer in
                    UIColor.white.setFill(); renderer.fill(CGRect(x: 0, y: 0, width: 120, height: 100))
                    let c = renderer.cgContext
                    c.translateBy(x: 0, y: 100); c.scaleBy(x: 1, y: -1)
                    c.textPosition = CGPoint(x: 20, y: 40)
                    CTLineDraw(line, c)
                }
                let name = "\(text)-\(halfPoints).png"
                try? png.write(to: output.appendingPathComponent(name))
                let runs = CTLineGetGlyphRuns(line) as! [CTRun]
                let fonts = runs.map { run -> String in
                    let attributes = CTRunGetAttributes(run) as NSDictionary
                    let actual = attributes[kCTFontAttributeName] as! CTFont
                    return CTFontCopyPostScriptName(actual) as String
                }
                metadata.append(["file": name, "text": text, "pointSize": size,
                    "baselinePixels": 180, "fonts": fonts])
            }
        }
        if let data = try? JSONSerialization.data(withJSONObject: metadata) { try? data.write(to: output.appendingPathComponent("metadata.json")) }
    }

    @objc private func changeAppearance(_ sender: UISegmentedControl) {
        overrideUserInterfaceStyle = sender.selectedSegmentIndex == 0 ? .light : .dark
        input.reloadInputViews()
    }
}

final class ReferenceSceneDelegate: UIResponder, UIWindowSceneDelegate {
    var window: UIWindow?
    func scene(_ scene: UIScene, willConnectTo session: UISceneSession,
               options connectionOptions: UIScene.ConnectionOptions) {
        guard let windowScene = scene as? UIWindowScene else { return }
        let window = UIWindow(windowScene: windowScene)
        window.rootViewController = ReferenceController()
        window.makeKeyAndVisible()
        self.window = window
    }
}

final class ReferenceDelegate: UIResponder, UIApplicationDelegate {
    func application(_ application: UIApplication,
                     configurationForConnecting session: UISceneSession,
                     options: UIScene.ConnectionOptions) -> UISceneConfiguration {
        let configuration = UISceneConfiguration(name: "Reference", sessionRole: session.role)
        configuration.delegateClass = ReferenceSceneDelegate.self
        return configuration
    }
}



/// Visible per-refresh timecode prevents the recorder from coalescing idle frames.
/// Public event observation records only this disposable reference application's events.
final class FidelityTraceApplication: UIApplication {
    static var eventSerial: UInt32 = 0
    static var events: [[String: Any]] = []
    private static let exportQueue = DispatchQueue(label: "reference.event-export")
    override func sendEvent(_ event: UIEvent) {
        if ProcessInfo.processInfo.arguments.contains("--fidelity-clock"), let touches = event.allTouches {
            for touch in touches {
                Self.eventSerial &+= 1
                let p = touch.location(in: touch.window)
                Self.events.append(["serial": Self.eventSerial, "time": touch.timestamp,
                    "phase": touch.phase.rawValue, "x": p.x, "y": p.y])
            }
            let snapshot = Self.events
            let suffix = ProcessInfo.processInfo.arguments.contains("--motion-contrast")
                ? (ProcessInfo.processInfo.arguments.contains("--dark") ? "-Dark" : "-Light") : ""
            Self.exportQueue.async {
                if let data = try? JSONSerialization.data(withJSONObject: snapshot) {
                    try? data.write(to: FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
                        .appendingPathComponent("touch-events\(suffix).json"), options: .atomic)
                }
            }
        }
        super.sendEvent(event)
    }
}

final class FidelityClockView: UIView {
    private var link: CADisplayLink?
    private var frameSerial: UInt32 = 0
    private var tick: UInt32 = 0
    override func didMoveToWindow() {
        super.didMoveToWindow()
        link?.invalidate()
        guard window != nil else { return }
        let link = CADisplayLink(target: self, selector: #selector(step(_:)))
        link.preferredFrameRateRange = CAFrameRateRange(minimum: 60, maximum: 60, preferred: 60)
        link.add(to: .main, forMode: .common)
        self.link = link
    }
    @objc private func step(_ link: CADisplayLink) {
        frameSerial &+= 1
        tick = UInt32(truncatingIfNeeded: UInt64(link.timestamp * 1000))
        setNeedsDisplay()
    }
    override func draw(_ rect: CGRect) {
        guard let c = UIGraphicsGetCurrentContext() else { return }
        for (row, value) in [frameSerial, tick, FidelityTraceApplication.eventSerial].enumerated() {
            for bit in 0..<32 {
                c.setFillColor(((value >> bit) & 1 == 1 ? UIColor.white : UIColor.black).cgColor)
                c.fill(CGRect(x: bit * 10, y: row * 10, width: 10, height: 10))
            }
        }
    }
}

UIApplicationMain(CommandLine.argc, CommandLine.unsafeArgv, NSStringFromClass(FidelityTraceApplication.self), NSStringFromClass(ReferenceDelegate.self))
