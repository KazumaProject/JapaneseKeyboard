import UIKit

// Uses the system keyboard; no custom inputView, appearance proxy, or private API.
final class ReferenceController: UIViewController {
    private let input = UITextField()

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .systemBackground
        overrideUserInterfaceStyle = .light

        let appearance = UISegmentedControl(items: ["Light", "Dark"])
        appearance.selectedSegmentIndex = 0
        appearance.addTarget(self, action: #selector(changeAppearance(_:)), for: .valueChanged)
        appearance.accessibilityIdentifier = "reference.appearance"

        input.borderStyle = .roundedRect
        input.placeholder = "System keyboard reference"
        input.autocorrectionType = .default
        input.spellCheckingType = .default
        input.autocapitalizationType = .sentences
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
        NSLayoutConstraint.activate([
            stack.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 24),
            stack.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor, constant: 20),
            stack.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor, constant: -20),
            input.heightAnchor.constraint(equalToConstant: 44)
        ])
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

UIApplicationMain(CommandLine.argc, CommandLine.unsafeArgv, nil, NSStringFromClass(ReferenceDelegate.self))
