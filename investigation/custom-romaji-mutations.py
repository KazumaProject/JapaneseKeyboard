"""Run after Gradle has resolved dependencies; JAVA_HOME must point to a JDK.

Compiles the real converter and its JUnit tests in temporary directories. The
baseline must pass, and each deliberately broken variant must fail. No production
source or build output is modified. Run from the repository root.
"""
from pathlib import Path
import subprocess, os, tempfile
root=Path.cwd(); cache=Path.home()/'.gradle/caches/modules-2/files-2.1'
def jar(group, artifact, version='*'):
 return next(p for p in (cache/group/artifact).glob(version+'/*/*.jar') if not p.name.endswith(('-sources.jar','-javadoc.jar')))
stdlib=jar('org.jetbrains.kotlin','kotlin-stdlib','1.9.22')
compiler=[jar('org.jetbrains.kotlin','kotlin-compiler-embeddable','1.9.22'),stdlib,jar('org.jetbrains.kotlin','kotlin-script-runtime','1.9.22'),jar('org.jetbrains.kotlin','kotlin-reflect','1.6.10'),jar('org.jetbrains.intellij.deps','trove4j'),jar('org.jetbrains','annotations')]
runtime=[stdlib,jar('junit','junit','4.13.2'),jar('org.hamcrest','hamcrest-core'),jar('com.google.code.gson','gson')]
java=str(Path(os.environ['JAVA_HOME'])/'bin/java')
source=(root/'app/src/main/java/com/kazumaproject/markdownhelperkeyboard/ime_service/romaji_kana/CustomRomajiScreenConverter.kt').read_text()
cases={'baseline': source, 'consume_both': source.replace('append("っ")\n                index++','append("っ")\n                index += 2'), 'miss_half_width': source.replace('current in SOKUON_CONSONANTS','text[index] in \'ａ\'..\'ｚ\' && current in SOKUON_CONSONANTS'), 'ignore_n_setting':source.replace('if (autoN &&','if (true &&')}
for name, text in cases.items():
 with tempfile.TemporaryDirectory(prefix='romaji-'+name) as temp:
  p=Path(temp);(p/'Converter.kt').write_text(text)
  run=subprocess.run([java,'-cp',os.pathsep.join(map(str,compiler)),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',os.pathsep.join(map(str,runtime)),'-d',str(p/'classes'),str(p/'Converter.kt'),str(root/'app/src/test/java/com/kazumaproject/markdownhelperkeyboard/ime_service/romaji_kana/CustomRomajiScreenConverterTest.kt')],capture_output=True,text=True)
  if run.returncode: raise RuntimeError(run.stderr)
  cp=[p/'classes',root/'app/src/test/resources']+runtime
  test=subprocess.run([java,'-cp',os.pathsep.join(map(str,cp)),'org.junit.runner.JUnitCore','com.kazumaproject.markdownhelperkeyboard.ime_service.romaji_kana.CustomRomajiScreenConverterTest'],capture_output=True,text=True)
  print(name, test.stdout[-500:],flush=True)
  assert (test.returncode==0)==(name=='baseline'),test.stdout+test.stderr
