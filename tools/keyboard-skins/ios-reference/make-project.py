#!/usr/bin/env python3
"""Generate the small reference Xcode project without third-party dependencies."""
from pathlib import Path
import plistlib

root = Path(__file__).resolve().parent
out = root / 'KeyboardReference.xcodeproj'
out.mkdir(exist_ok=True)
objects = {}

def obj(identifier, isa, **values):
    objects[identifier] = dict(isa=isa, **values)
    return identifier

obj('MAIN_SOURCE', 'PBXFileReference', lastKnownFileType='sourcecode.swift', path='main.swift', sourceTree='<group>')
obj('TEST_SOURCE', 'PBXFileReference', lastKnownFileType='sourcecode.swift', path='ReferenceUITests.swift', sourceTree='<group>')
obj('APP_PRODUCT', 'PBXFileReference', explicitFileType='wrapper.application', path='KeyboardReference.app', sourceTree='BUILT_PRODUCTS_DIR')
obj('TEST_PRODUCT', 'PBXFileReference', explicitFileType='wrapper.cfbundle', path='ReferenceUITests.xctest', sourceTree='BUILT_PRODUCTS_DIR')
obj('MAIN_BUILD', 'PBXBuildFile', fileRef='MAIN_SOURCE')
obj('TEST_BUILD', 'PBXBuildFile', fileRef='TEST_SOURCE')
obj('GROUP', 'PBXGroup', children=['MAIN_SOURCE', 'TEST_SOURCE', 'PRODUCTS'], sourceTree='<group>')
obj('PRODUCTS', 'PBXGroup', children=['APP_PRODUCT', 'TEST_PRODUCT'], name='Products', sourceTree='<group>')
for prefix, source in [('APP', 'MAIN_BUILD'), ('TEST', 'TEST_BUILD')]:
    obj(prefix+'_SOURCES', 'PBXSourcesBuildPhase', buildActionMask=2147483647, files=[source], runOnlyForDeploymentPostprocessing=0)
    obj(prefix+'_FRAMEWORKS', 'PBXFrameworksBuildPhase', buildActionMask=2147483647, files=[], runOnlyForDeploymentPostprocessing=0)
    settings = dict(SWIFT_VERSION='5.0', SDKROOT='iphonesimulator', IPHONEOS_DEPLOYMENT_TARGET='26.0',
                    TARGETED_DEVICE_FAMILY='1', CODE_SIGNING_ALLOWED='NO', PRODUCT_NAME='$(TARGET_NAME)',
                    SWIFT_OPTIMIZATION_LEVEL='-Onone', ENABLE_TESTABILITY='YES')
    if prefix == 'APP':
        settings.update(PRODUCT_BUNDLE_IDENTIFIER='com.kazumaproject.keyboard-skins.reference', INFOPLIST_FILE='Info.plist')
    else:
        settings.update(PRODUCT_BUNDLE_IDENTIFIER='com.kazumaproject.keyboard-skins.reference.uitests',
                        GENERATE_INFOPLIST_FILE='YES', TEST_TARGET_NAME='KeyboardReference')
    obj(prefix+'_DEBUG', 'XCBuildConfiguration', buildSettings=settings, name='Debug')
    obj(prefix+'_CONFIG', 'XCConfigurationList', buildConfigurations=[prefix+'_DEBUG'], defaultConfigurationIsVisible=0, defaultConfigurationName='Debug')
obj('PROJ_DEBUG', 'XCBuildConfiguration', buildSettings={}, name='Debug')
obj('PROJ_CONFIG', 'XCConfigurationList', buildConfigurations=['PROJ_DEBUG'], defaultConfigurationIsVisible=0, defaultConfigurationName='Debug')
obj('APP_TARGET', 'PBXNativeTarget', buildConfigurationList='APP_CONFIG', buildPhases=['APP_SOURCES','APP_FRAMEWORKS'], buildRules=[], dependencies=[], name='KeyboardReference', productName='KeyboardReference', productReference='APP_PRODUCT', productType='com.apple.product-type.application')
obj('PROXY', 'PBXContainerItemProxy', containerPortal='PROJECT', proxyType=1, remoteGlobalIDString='APP_TARGET', remoteInfo='KeyboardReference')
obj('DEP', 'PBXTargetDependency', target='APP_TARGET', targetProxy='PROXY')
obj('TEST_TARGET', 'PBXNativeTarget', buildConfigurationList='TEST_CONFIG', buildPhases=['TEST_SOURCES','TEST_FRAMEWORKS'], buildRules=[], dependencies=['DEP'], name='ReferenceUITests', productName='ReferenceUITests', productReference='TEST_PRODUCT', productType='com.apple.product-type.bundle.ui-testing')
obj('PROJECT', 'PBXProject', attributes={'LastUpgradeCheck':'2600','TargetAttributes': {'TEST_TARGET': {'TestTargetID':'APP_TARGET'}}}, buildConfigurationList='PROJ_CONFIG', compatibilityVersion='Xcode 14.0', developmentRegion='en', hasScannedForEncodings=0, knownRegions=['en','Base'], mainGroup='GROUP', productRefGroup='PRODUCTS', projectDirPath='', projectRoot='', targets=['APP_TARGET','TEST_TARGET'])
with (out/'project.pbxproj').open('wb') as f:
    plistlib.dump(dict(archiveVersion='1', classes={}, objectVersion='56', objects=objects, rootObject='PROJECT'), f)
schemes = out/'xcshareddata'/'xcschemes'
schemes.mkdir(parents=True, exist_ok=True)
(schemes/'KeyboardReference.xcscheme').write_text('''<?xml version="1.0" encoding="UTF-8"?>
<Scheme LastUpgradeVersion="2600" version="1.3">
<BuildAction parallelizeBuildables="YES" buildImplicitDependencies="YES"><BuildActionEntries>
<BuildActionEntry buildForTesting="YES" buildForRunning="YES" buildForProfiling="NO" buildForArchiving="NO" buildForAnalyzing="YES"><BuildableReference BuildableIdentifier="primary" BlueprintIdentifier="APP_TARGET" BuildableName="KeyboardReference.app" BlueprintName="KeyboardReference" ReferencedContainer="container:KeyboardReference.xcodeproj"/></BuildActionEntry>
</BuildActionEntries></BuildAction>
<TestAction buildConfiguration="Debug" selectedDebuggerIdentifier="Xcode.DebuggerFoundation.Debugger.LLDB" selectedLauncherIdentifier="Xcode.IDEFoundation.Launcher.LLDB" shouldUseLaunchSchemeArgsEnv="YES"><Testables><TestableReference skipped="NO"><BuildableReference BuildableIdentifier="primary" BlueprintIdentifier="TEST_TARGET" BuildableName="ReferenceUITests.xctest" BlueprintName="ReferenceUITests" ReferencedContainer="container:KeyboardReference.xcodeproj"/></TestableReference></Testables></TestAction>
<LaunchAction buildConfiguration="Debug" selectedDebuggerIdentifier="Xcode.DebuggerFoundation.Debugger.LLDB" selectedLauncherIdentifier="Xcode.IDEFoundation.Launcher.LLDB" launchStyle="0" useCustomWorkingDirectory="NO" ignoresPersistentStateOnLaunch="NO" debugDocumentVersioning="YES" debugServiceExtension="internal" allowLocationSimulation="YES"><BuildableProductRunnable runnableDebuggingMode="0"><BuildableReference BuildableIdentifier="primary" BlueprintIdentifier="APP_TARGET" BuildableName="KeyboardReference.app" BlueprintName="KeyboardReference" ReferencedContainer="container:KeyboardReference.xcodeproj"/></BuildableProductRunnable></LaunchAction>
</Scheme>
''')
print(out)

# Schemes resolve buildable references through canonical Xcode object identifiers.
import hashlib
ids = {key: hashlib.sha256(key.encode()).hexdigest()[:24].upper() for key in objects}
def canonical(value):
    if isinstance(value, dict):
        return {ids.get(k, k): canonical(v) for k, v in value.items()}
    if isinstance(value, list):
        return [canonical(v) for v in value]
    if isinstance(value, str):
        return ids.get(value, value)
    return value
with (out/'project.pbxproj').open('wb') as f:
    plistlib.dump(canonical(dict(archiveVersion='1', classes={}, objectVersion='56', objects=objects, rootObject='PROJECT')), f)
p = schemes/'KeyboardReference.xcscheme'
s = p.read_text()
for key, value in ids.items():
    s = s.replace('BlueprintIdentifier="'+key+'"', 'BlueprintIdentifier="'+value+'"')
p.write_text(s)
