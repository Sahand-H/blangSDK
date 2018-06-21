package blang.runtime.internals;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Repository;

import com.google.common.base.Joiner;

import binc.Command;
import binc.Command.BinaryExecutionException;
import blang.runtime.Runner;
import blang.runtime.internals.Main;
import briefj.BriefIO;
import briefj.BriefStrings;
import briefj.repo.RepositoryUtils;
import briefj.run.Results;

public class StandaloneCompiler  {
  
  private final File blangHome;
  private final File projectHome;
  private final File compilationFolder;
  private final File compilationPool;
  private final Path srcFolder;
  private final List<String> dependencies = loadDependencies();
  
  public StandaloneCompiler() {
    
    this.blangHome = findBlangHome();
    this.projectHome = new File(".");
    this.compilationFolder = Results.getFolderInResultFolder(COMPILATION_DIR_NAME);
    this.compilationPool = compilationFolder.getParentFile().getParentFile();
    this.srcFolder = Paths.get(compilationFolder.getPath(), "src", "main", "java");
    init();
  }
  
  private List<String> loadDependencies() {
    List<String> result = new ArrayList<>();
    File dependencies = new File("dependencies.txt");
    if (dependencies.exists())
      for (String line : BriefIO.readLines(dependencies))
        result.add(line.trim());
    return result;
  }

  @Override
  public String toString() {
    return 
      "Blang home folder: " + blangHome.getAbsolutePath() + "\n" +
      "Project folder: " + compilationFolder.getAbsolutePath();
  }

  File findBlangHome() {
    File file = RepositoryUtils.findSourceFile(this);
    while (!new File(file, BUILD_FILE).exists() && file.getParent() != null) {
      file = file.getParentFile();
    }
    if (new File(file, BUILD_FILE).exists()) {
      return file;
    } else {
      throw new RuntimeException("Blang home cannot be located.");
    }
  }
  
  public Repository getBlangSDKRepository() {
    try {
      return new FileRepository(new File(blangHome, ".git"));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
  
  final static String BUILD_FILE = "build.gradle";
  
  public String compileProject() {
    return compile(compilationFolder, COMPILATION_DIR_NAME);
  }
  
  public String compileBlang() {
    return compile(blangHome, "blang");
  }
  
  /**
   * 
   * @return classpath-formatted list of jars created or that the build task depends on
   */
  public static String compile(File folder, String projectName) throws BinaryExecutionException {
    runGradle("build", folder);
    return "" +
        parseClasspath(runGradle("printClasspath", folder)) + // dependencies
        File.pathSeparator +                             
                                                      // plus newly compiled file:
        Paths.get(folder.getPath(), "build", "libs", projectName + ".jar").toAbsolutePath();
  }
  
  
  private static String runGradle(String gradleTaskName, File folder) throws BinaryExecutionException  {
    Command gradleCmd = 
        Command.byName("gradle")
          .appendArg(gradleTaskName)
          .appendArg("--no-daemon") // Avoid zombie processes; gradle options allowed both after and before
          .ranIn(folder)
          .throwOnNonZeroReturnCode();
    return Command.call(gradleCmd);
  }
  
  public void runCompiledModel(String classpath, String [] args) {
    Command runnerCmd = javaCommand()
        .withStandardOutMirroring()
        .throwOnNonZeroReturnCode()
        .appendArg("-cp").appendArg(classpath)
        .appendArg(Runner.class.getTypeName());
    for (String arg : args) {
      runnerCmd = runnerCmd.appendArg(arg);
    }
    Command.call(runnerCmd);
  }
  
  public Supplier<Integer> getBlangRestarter(String [] args) {
    return () -> {
      // build and collect classpath
      String classPath = compileBlang();
      // restart
      Command restart = javaCommand()
          .throwOnNonZeroReturnCode()
          .withStandardOutMirroring()
          .appendArg("-classpath").appendArg(classPath)
          .appendArg(Main.class.getTypeName());
      for (String arg : args) {
        restart = restart.appendArg(arg);
      }
      try {
        Command.call(restart);
        return 0;
      } catch (BinaryExecutionException bee) {
        return 1;
      }
    };
  }
  
  // test
  
  public static Command javaCommand()
  {
    Command javaCmd = Command.byPath(Paths.get(System.getProperty("java.home"), "bin", "java").toFile());
    
    // get Xmx options such as -Xmx1g, etc
    for (String jvmArgument : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
      javaCmd = javaCmd.appendArg(jvmArgument);
    }
    
    return javaCmd;
  }
  
  private static String parseClasspath(String gradleOutput) {
    List<String> items = new ArrayList<>();
    for (String line : gradleOutput.split("\\r?\\n"))
      if (line.matches("^.*[.]jar\\s*$"))
        items.add(line.replaceAll("\\s+", ""));
    if (items.isEmpty())
      throw new RuntimeException("Compilation infrastructure setup failed (could not form classpath of dependencies: \n" + gradleOutput);
    return Joiner.on(File.pathSeparator).join(items);
  }

  private void init() {
    
    try { 
      // TODO: detect if already have a gradle setup
      // TODO: later, use always same and symlink if possible to save time
      
      setupBuildFiles();
      Files.createDirectories(srcFolder);
      Files.walkFileTree(projectHome.toPath(), new FileTransferProcessor()); 
    }
    catch (Exception e) { throw new RuntimeException(e); }
  }
  
  private static final PathMatcher BLANG_MATCHER = FileSystems.getDefault().getPathMatcher("glob:**.{java,bl,xtend}");
  
  class FileTransferProcessor extends SimpleFileVisitor<Path> {
    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
      if (BLANG_MATCHER.matches(file)) {
        Path target = srcFolder.resolve(file.getFileName());
        Files.copy(file, target);
      }
      return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
      if (dir.normalize().equals(compilationPool.toPath().normalize())) {
        return FileVisitResult.SKIP_SUBTREE;
      } else {
        return FileVisitResult.CONTINUE;
      }
    }
  }

  String sdkVersion;
  private void setupBuildFiles() {
    final String buildFileName = BUILD_FILE;
    String buildFileContents = BriefIO.fileToString(new File(blangHome, buildFileName));
    // find version
    sdkVersion = processDirective(buildFileContents, Directive.EXTRACT_VERSION);
    // add blangSDK dependency
    buildFileContents = processDirective(buildFileContents, Directive.ADD_SDK_DEPENDENCY);
    // remove deployment info
    buildFileContents = processDirective(buildFileContents, Directive.TRIM);
    File generatedBuildFile = new File(compilationFolder, buildFileName);
    BriefIO.write(generatedBuildFile, buildFileContents);
  }
  
  private static enum Directive {
    EXTRACT_VERSION {
      @Override
      String process(String buildFileContents, String line, StandaloneCompiler compiler) {
        return BriefStrings.firstGroupFromFirstMatch(".*\"(.*)\".*", line);
      }
    },
    ADD_SDK_DEPENDENCY {
      @Override
      String process(String buildFileContents, String line, StandaloneCompiler compiler) {
        String depLine = "  compile group: 'ca.ubc.stat', name: 'blangSDK', version: '" + compiler.sdkVersion + "'";
        for (String dep : compiler.dependencies)
          depLine += "\n  compile '" + dep + "'";
        return buildFileContents.replace(line, depLine);
      }
    },
    TRIM {
      @Override
      String process(String buildFileContents, String line, StandaloneCompiler compiler) {
        int index = buildFileContents.indexOf(line);
        return buildFileContents.substring(0, index);
      }
    }
    ;
    abstract String process(String buildFileContents, String line, StandaloneCompiler compiler);
  }

  private String processDirective(String buildFileContents, Directive directive) {
    // find the line containing the directive
    String line = findDirectiveLine(buildFileContents, directive);
    return directive.process(buildFileContents, line, this);
  }

  private String findDirectiveLine(String buildFileContents, Directive directive) {
    for (String line : buildFileContents.split("\\r?\\n"))
      if (line.contains(directive.toString()))
        return line;
    throw new RuntimeException();
  }

  public static final String COMPILATION_DIR_NAME = "blang-compilation";
}
