#!/usr/bin/env python3
"""Build the pinned LiteRT-LM scoring bindings; keep fetched sources and binaries in a cache."""

import argparse
import fcntl
import hashlib
import os
from pathlib import Path
import shutil
import struct
import subprocess
import tarfile
import tempfile
import urllib.request
from zipfile import ZipFile, ZIP_DEFLATED


REVISION = "f73637c57f0940b53da184e0d5adfc52a4e55eef"
SOURCE_SHA256 = "c083fa3bebea1c29ef29ea549d84b97fc566ac2a477cc2ed2c0eb054616f0a3d"
AAR_SHA256 = "b53b244c47d1711d9d9250436e20130be552b8b297affdf165ca426ce888d0ca"
PACKAGE = "kotlin/java/com/google/ai/edge/litertlm"
CONFIGS = {"arm64-v8a": ("android_arm64", 183), "x86_64": ("android_x86_64", 62)}


def run(args, cwd, **kwargs):
    return subprocess.run(args, cwd=cwd, check=True, **kwargs)


def download_verified(url, destination, digest):
    if not destination.is_file():
        partial = destination.with_suffix(".download")
        urllib.request.urlretrieve(url, partial)
        partial.replace(destination)
    actual = hashlib.file_digest(destination.open("rb"), "sha256").hexdigest()
    if actual != digest:
        raise RuntimeError("SHA-256 mismatch: " + str(destination))


def prepare(args):
    patch = args.patch.resolve()
    abis = args.abis.split(",")
    if not abis or any(abi not in CONFIGS for abi in abis):
        raise ValueError("Gemma ABIs must be arm64-v8a and/or x86_64")
    cache = args.cache.resolve()
    cache.mkdir(parents=True, exist_ok=True)
    key = hashlib.sha256(REVISION.encode() + patch.read_bytes() + Path(__file__).read_bytes() +
                         args.abis.encode() + args.ndk.encode()).hexdigest()[:24]
    cached_aar = cache / key / "litertlm-scoring.aar"
    with (cache / "build.lock").open("w") as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        if not cached_aar.is_file():
            bazel = shutil.which("bazelisk") or shutil.which("bazel")
            if not bazel:
                raise RuntimeError("Install Bazelisk to build the Gemma scoring runtime.")
            source = cache / ("source-" + REVISION)
            patch_stamp = source / ".scoring-patch-sha256"
            digest = hashlib.sha256(patch.read_bytes()).hexdigest()
            if not patch_stamp.is_file() or patch_stamp.read_text() != digest:
                archive = cache / ("source-" + REVISION + ".tar.gz")
                download_verified("https://codeload.github.com/google-ai-edge/LiteRT-LM/tar.gz/" + REVISION,
                                  archive, SOURCE_SHA256)
                source.mkdir(parents=True, exist_ok=True)
                # The verified archive restores only task-owned sources before applying this patch.
                # GitHub's source archive includes the required LFS prebuilts; git-lfs is unnecessary.
                with tarfile.open(archive) as bundle:
                    prefix = "LiteRT-LM-" + REVISION + "/"
                    for member in bundle:
                        if member.name.startswith(prefix):
                            member.name = member.name[len(prefix):]
                            if member.name:
                                bundle.extract(member, source, filter="data")
                run(["git", "apply", "--check", str(patch)], source)
                run(["git", "apply", str(patch)], source)
                patch_stamp.write_text(digest)
            env = os.environ.copy()
            sdk = args.sdk.resolve()
            env["ANDROID_HOME"] = str(sdk)
            env["ANDROID_SDK_ROOT"] = str(sdk)
            ndk = sdk / "ndk" / args.ndk
            if not ndk.is_dir():
                run([str(sdk / "cmdline-tools/latest/bin/sdkmanager"), "ndk;" + args.ndk], source)
            env["ANDROID_NDK_HOME"] = str(ndk)
            cached_aar.parent.mkdir(parents=True, exist_ok=True)
            with tempfile.TemporaryDirectory(prefix="package-", dir=cached_aar.parent) as temporary:
                stage = Path(temporary)
                classes = None
                for abi in abis:
                    config, machine = CONFIGS[abi]
                    flags = ["--config=" + config, "-c", "opt", "--jobs=" + str(args.jobs),
                             "--local_ram_resources=6144", "--linkopt=-Wl,-z,max-page-size=16384",
                             "--noshow_progress"]
                    # The Android rule's outer JAR only exports its Kotlin implementation JAR.
                    run([bazel, "build", *flags, "//" + PACKAGE + ":litertlm-android_kt",
                         "//" + PACKAGE + "/jni:litertlm_jni"], source, env=env)
                    native = source / "bazel-bin" / PACKAGE / "jni/liblitertlm_jni.so"
                    header = native.read_bytes()[:20]
                    if header[:4] != b"\x7fELF" or struct.unpack_from("<H", header, 18)[0] != machine:
                        raise RuntimeError("Native scoring library ABI mismatch: " + abi)
                    destination = stage / "jni" / abi / native.name
                    destination.parent.mkdir(parents=True)
                    shutil.copyfile(native, destination)
                    # The source-built JNI library links this upstream runtime dependency.
                    constraint = source / "prebuilt" / config / "libGemmaModelConstraintProvider.so"
                    with constraint.open("rb") as library:
                        dependency_header = library.read(20)
                    if (dependency_header[:4] != b"\x7fELF" or
                            struct.unpack_from("<H", dependency_header, 18)[0] != machine):
                        raise RuntimeError("Constraint library ABI mismatch: " + abi)
                    shutil.copyfile(constraint, destination.parent / constraint.name)
                    if classes is None:
                        outputs = subprocess.check_output(
                            [bazel, "cquery", *flags, "--output=files", "//" + PACKAGE + ":litertlm-android_kt"],
                            cwd=source, env=env, text=True,
                        ).splitlines()
                        for output in outputs:
                            if not output.endswith(".jar") or output.endswith("-src.jar"):
                                continue
                            jar = source / output
                            with ZipFile(jar) as candidate:
                                entry = "com/google/ai/edge/litertlm/Engine.class"
                                if entry in candidate.namelist() and b"renderScoringPrompt" in candidate.read(entry):
                                    classes = stage / "classes.jar"
                                    shutil.copyfile(jar, classes)
                                    break
                        if classes is None:
                            raise RuntimeError("Could not locate the compiled scoring Kotlin classes")
                upstream = cache / "litertlm-android-0.14.0.aar"
                download_verified("https://dl.google.com/dl/android/maven2/com/google/ai/edge/litertlm/"
                                  "litertlm-android/0.14.0/litertlm-android-0.14.0.aar", upstream, AAR_SHA256)
                with ZipFile(cached_aar.with_suffix(".tmp"), "w", ZIP_DEFLATED) as archive:
                    # Preserve upstream metadata, license and third-party notices.
                    with ZipFile(upstream) as original:
                        for name in original.namelist():
                            if name != "classes.jar" and not name.startswith("jni/"):
                                archive.writestr(name, original.read(name))
                    for path in sorted(stage.rglob("*")):
                        if path.is_file():
                            archive.write(path, path.relative_to(stage))
                cached_aar.with_suffix(".tmp").replace(cached_aar)
        args.output.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(cached_aar, args.output)
        print("Gemma scoring runtime:", args.output, flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--patch", type=Path, required=True)
    parser.add_argument("--cache", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--sdk", type=Path, required=True)
    parser.add_argument("--ndk", default="29.0.14206865")
    parser.add_argument("--abis", default="arm64-v8a,x86_64")
    parser.add_argument("--jobs", type=int, default=4)
    prepare(parser.parse_args())
