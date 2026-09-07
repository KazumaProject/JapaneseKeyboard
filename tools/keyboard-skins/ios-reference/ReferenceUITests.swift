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
