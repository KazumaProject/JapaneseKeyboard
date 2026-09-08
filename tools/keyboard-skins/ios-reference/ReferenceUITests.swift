import XCTest

/// Run only against the disposable reference app on the selected simulator.
/// A simultaneous simctl recording is needed to observe the held states.
final class ReferenceUITests: XCTestCase {
    func testConfigureEnglishUS() throws {
        let settings = XCUIApplication(bundleIdentifier: "com.apple.Preferences")
        settings.launch()
        settings.buttons["com.apple.settings.general"].tap()
        settings.buttons["Keyboard"].tap()
        settings.buttons["KEYBOARDS"].tap()
        settings.buttons["AddNewKeyboard"].tap()
        let search = settings.searchFields.firstMatch
        search.tap()
        search.typeText("English")
        attach(settings, "english-options")
        settings.descendants(matching: .any).matching(identifier: "en_US").firstMatch.tap()
        attach(settings, "english-us-added")
    }

    func testEnglishReference() throws {
        continueAfterFailure = false
        let app = XCUIApplication(bundleIdentifier: "com.kazumaproject.keyboard-skins.reference")
        app.launch()
        app.textFields["reference.input"].tap()
        if app.buttons["Continue"].waitForExistence(timeout: 2) { app.buttons["Continue"].tap() }
        let next = app.buttons["Next keyboard"].firstMatch
        next.press(forDuration: 1.2)
        let us = app.cells.matching(NSPredicate(format: "label CONTAINS 'English' AND (label CONTAINS 'US' OR label CONTAINS 'United States')")).firstMatch
        XCTAssertTrue(us.exists)
        us.tap()
        if app.buttons["Continue"].waitForExistence(timeout: 2) { app.buttons["Continue"].tap() }
        for appearance in ["Light", "Dark"] {
            app.segmentedControls["reference.appearance"].buttons[appearance].tap()
            Thread.sleep(forTimeInterval: 0.4)
            attach(app, "\(appearance)-qwerty-idle")
            for letter in ["q", "e", "p", "a", "l"] {
                let key = app.keys.matching(NSPredicate(format: "label ==[c] %@", letter)).firstMatch
                XCTAssertTrue(key.isHittable)
                print("CAPTURE_BEGIN \(appearance)-qwerty-\(letter)-hold \(Date().timeIntervalSince1970)")
                key.press(forDuration: 2)
                print("CAPTURE_END \(Date().timeIntervalSince1970)")
            }
        }
    }

    func testReferenceMatrix() throws {
        continueAfterFailure = false
        let app = XCUIApplication(bundleIdentifier: "com.kazumaproject.keyboard-skins.reference")
        app.launch()
        let field = app.textFields["reference.input"]
        XCTAssertTrue(field.waitForExistence(timeout: 10))
        field.tap()
        attach(app, "initial-keyboard")
        XCTAssertLessThan(app.keyboards.firstMatch.frame.minY, app.frame.height)
        let next = app.buttons.matching(NSPredicate(format:
            "label == 'Next keyboard' OR label == '次のキーボード'")).firstMatch
        XCTAssertTrue(next.exists)
        next.press(forDuration: 1.2)
        app.cells["日本語かな"].tap()
        for appearance in ["Light", "Dark"] {
            app.segmentedControls["reference.appearance"].buttons[appearance].tap()
            Thread.sleep(forTimeInterval: 0.4)
            attach(app, "\(appearance)-kana-idle")
            for label in ["あ", "か", "さ", "わ"] {
                let key = app.keys[label]
                XCTAssertTrue(key.exists)
                XCTContext.runActivity(named: "\(appearance)-kana-\(label)-hold") { _ in
                    print("CAPTURE_BEGIN \(appearance)-kana-\(label)-hold \(Date().timeIntervalSince1970)")
                    key.press(forDuration: 2.0)
                    print("CAPTURE_END \(Date().timeIntervalSince1970)")
                }
            }
            for (direction, vector) in [("left", CGVector(dx: -60, dy: 0)),
                                        ("up", CGVector(dx: 0, dy: -60)),
                                        ("right", CGVector(dx: 60, dy: 0)),
                                        ("down", CGVector(dx: 0, dy: 60))] {
                let origin = app.keys["な"].coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5))
                XCTContext.runActivity(named: "\(appearance)-kana-flick-\(direction)") { _ in
                    print("CAPTURE_BEGIN \(appearance)-kana-flick-\(direction) \(Date().timeIntervalSince1970)")
                    origin.press(forDuration: 0.1, thenDragTo: origin.withOffset(vector),
                                 withVelocity: XCUIGestureVelocity(rawValue: 300), thenHoldForDuration: 1.5)
                    print("CAPTURE_END \(Date().timeIntervalSince1970)")
                }
            }
        }
        next.press(forDuration: 1.2)
        attach(app, "english-menu")
        let us = app.cells.matching(NSPredicate(format: "label CONTAINS 'English' AND (label CONTAINS 'US' OR label CONTAINS 'United States')")).firstMatch
        XCTAssertTrue(us.exists)
        us.tap()
        if app.buttons["Continue"].waitForExistence(timeout: 2) { app.buttons["Continue"].tap() }
        for appearance in ["Light", "Dark"] {
            app.segmentedControls["reference.appearance"].buttons[appearance].tap()
            Thread.sleep(forTimeInterval: 0.4)
            attach(app, "\(appearance)-qwerty-idle")
            for letter in ["q", "e", "p", "a", "l"] {
                let key = app.keys.matching(NSPredicate(format: "label ==[c] %@", letter)).firstMatch
                XCTAssertTrue(key.exists)
                print("CAPTURE_BEGIN \(appearance)-qwerty-\(letter)-hold \(Date().timeIntervalSince1970)")
                key.press(forDuration: 2)
                print("CAPTURE_END \(Date().timeIntervalSince1970)")
            }
        }

    }

    func testMotionReference() throws {
        continueAfterFailure = false
        let app = XCUIApplication(bundleIdentifier: "com.kazumaproject.keyboard-skins.reference")
        app.launchArguments = ["--fidelity-clock"]
        app.launch()
        app.textFields["reference.input"].tap()
        if app.buttons["Continue"].waitForExistence(timeout: 1) { app.buttons["Continue"].tap() }
        app.buttons["Next keyboard"].firstMatch.press(forDuration: 1.2)
        app.cells["日本語かな"].tap()
        for appearance in ["Light", "Dark"] {
            app.segmentedControls["reference.appearance"].buttons[appearance].tap()
            Thread.sleep(forTimeInterval: 0.5)
            for trial in 0..<3 {
                print("MOTION_BEGIN \(appearance)-kana-hold-\(trial) \(Date().timeIntervalSince1970)")
                app.keys["な"].press(forDuration: 1.2)
                Thread.sleep(forTimeInterval: 0.5)
            }
            for (direction, vector) in [("left", CGVector(dx: -60, dy: 0)), ("up", CGVector(dx: 0, dy: -60)),
                                        ("right", CGVector(dx: 60, dy: 0)), ("down", CGVector(dx: 0, dy: 60))] {
                print("MOTION_BEGIN \(appearance)-kana-\(direction) \(Date().timeIntervalSince1970)")
                let point = app.keys["な"].coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5))
                point.press(forDuration: 0.1, thenDragTo: point.withOffset(vector),
                    withVelocity: XCUIGestureVelocity(rawValue: 300), thenHoldForDuration: 0.5)
                Thread.sleep(forTimeInterval: 0.5)
            }
        }
        app.buttons["Next keyboard"].firstMatch.press(forDuration: 1.2)
        app.cells.matching(NSPredicate(format: "label CONTAINS 'English' AND (label CONTAINS 'US' OR label CONTAINS 'United States')")).firstMatch.tap()
        if app.buttons["Continue"].waitForExistence(timeout: 1) { app.buttons["Continue"].tap() }
        for appearance in ["Light", "Dark"] {
            app.segmentedControls["reference.appearance"].buttons[appearance].tap()
            Thread.sleep(forTimeInterval: 0.5)
            for trial in 0..<3 {
                for letter in ["q", "e", "p"] {
                    print("MOTION_BEGIN \(appearance)-qwerty-\(letter)-\(trial) \(Date().timeIntervalSince1970)")
                    let key = app.keys.matching(NSPredicate(format: "label ==[c] %@", letter)).firstMatch
                    XCTAssertTrue(key.isHittable)
                    key.press(forDuration: 1.2)
                    Thread.sleep(forTimeInterval: 0.5)
                }
            }
        }
        attach(app, "motion-final")
    }

    func testControlledReference() throws {
        continueAfterFailure = false
        let app = XCUIApplication(bundleIdentifier: "com.kazumaproject.keyboard-skins.reference")
        for appearance in ["Light", "Dark"] {
            app.launchArguments = ["--controlled"] + (appearance == "Dark" ? ["--dark"] : [])
            app.launch()
            app.textFields["reference.input"].tap()
            if app.buttons["Continue"].waitForExistence(timeout: 1) { app.buttons["Continue"].tap() }
            app.buttons["Next keyboard"].firstMatch.press(forDuration: 1.2)
            app.cells["日本語かな"].tap()
            for label in ["あ", "か", "さ", "わ"] {
                attach(app, "\(appearance)-kana-\(label)-before")
                print("CAPTURE_BEGIN \(appearance)-kana-\(label)-hold \(Date().timeIntervalSince1970)")
                app.keys[label].press(forDuration: 2)
            }
            for (direction, vector) in [("left", CGVector(dx: -60, dy: 0)), ("up", CGVector(dx: 0, dy: -60)),
                                        ("right", CGVector(dx: 60, dy: 0)), ("down", CGVector(dx: 0, dy: 60))] {
                attach(app, "\(appearance)-kana-flick-\(direction)-before")
                print("CAPTURE_BEGIN \(appearance)-kana-flick-\(direction) \(Date().timeIntervalSince1970)")
                let p = app.keys["な"].coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5))
                p.press(forDuration: 0.1, thenDragTo: p.withOffset(vector), withVelocity: XCUIGestureVelocity(rawValue: 300), thenHoldForDuration: 1.5)
            }
            app.buttons["Next keyboard"].firstMatch.press(forDuration: 1.2)
            app.cells.matching(NSPredicate(format: "label CONTAINS 'English' AND (label CONTAINS 'US' OR label CONTAINS 'United States')")).firstMatch.tap()
            if app.buttons["Continue"].waitForExistence(timeout: 1) { app.buttons["Continue"].tap() }
            for letter in ["q", "e", "p", "a", "l"] {
                let key = app.keys.matching(NSPredicate(format: "label ==[c] %@", letter)).firstMatch
                XCTAssertTrue(key.isHittable)
                attach(app, "\(appearance)-qwerty-\(letter)-before")
                print("CAPTURE_BEGIN \(appearance)-qwerty-\(letter)-hold \(Date().timeIntervalSince1970)")
                key.press(forDuration: 2)
            }
            attach(app, "\(appearance)-controlled-final")
            app.terminate()
        }
    }

    func testCenterPreviews() throws {
        continueAfterFailure = false
        let app = XCUIApplication(bundleIdentifier: "com.kazumaproject.keyboard-skins.reference")
        for appearance in ["Light", "Dark"] {
            app.launchArguments = ["--controlled"] + (appearance == "Dark" ? ["--dark"] : [])
            app.launch(); app.textFields["reference.input"].tap()
            app.buttons["Next keyboard"].firstMatch.press(forDuration: 1.2)
            app.cells.matching(NSPredicate(format: "label CONTAINS 'English' AND (label CONTAINS 'US' OR label CONTAINS 'United States')")).firstMatch.tap()
            for letter in ["e", "a", "l"] {
                let key = app.keys.matching(NSPredicate(format: "label ==[c] %@", letter)).firstMatch
                XCTAssertTrue(key.isHittable)
                attach(app, "\(appearance)-qwerty-\(letter)-preview-before")
                print("CAPTURE_FAST \(appearance)-qwerty-\(letter)-preview \(Date().timeIntervalSince1970)")
                key.press(forDuration: 0.3)
                Thread.sleep(forTimeInterval: 0.5)
            }
            app.terminate()
        }
    }

    func testQwertyMotionContrast() throws {
        continueAfterFailure = false
        let app = XCUIApplication(bundleIdentifier: "com.kazumaproject.keyboard-skins.reference")
        for appearance in ["Light", "Dark"] {
            app.launchArguments = ["--controlled", "--motion-contrast", "--fidelity-clock"] + (appearance == "Dark" ? ["--dark"] : [])
            app.launch(); app.textFields["reference.input"].tap()
            app.buttons["Next keyboard"].firstMatch.press(forDuration: 1.2)
            app.cells.matching(NSPredicate(format: "label CONTAINS 'English' AND (label CONTAINS 'US' OR label CONTAINS 'United States')")).firstMatch.tap()
            for letter in ["q", "e", "p"] {
                for trial in 0..<3 {
                    let key = app.keys.matching(NSPredicate(format: "label ==[c] %@", letter)).firstMatch
                    XCTAssertTrue(key.isHittable)
                    print("MOTION_BEGIN \(appearance)-\(letter)-\(trial)")
                    key.press(forDuration: letter == "e" ? 1.2 : 0.14)
                    Thread.sleep(forTimeInterval: 0.5)
                }
            }
            attach(app, "\(appearance)-contrast-final")
            app.terminate()
        }
    }

    func testFontSpecimens() {
        let app = XCUIApplication(bundleIdentifier: "com.kazumaproject.keyboard-skins.reference")
        app.launchArguments = ["--font-specimens"]
        app.launch()
        XCTAssertTrue(app.textFields["reference.input"].waitForExistence(timeout: 10))
    }

    private func attach(_ app: XCUIApplication, _ name: String) {
        let screenshot = XCTAttachment(screenshot: app.screenshot())
        screenshot.name = name
        screenshot.lifetime = .keepAlways
        add(screenshot)
        let hierarchy = XCTAttachment(string: app.debugDescription)
        hierarchy.name = name + "-hierarchy"
        hierarchy.lifetime = .keepAlways
        add(hierarchy)
    }
}
