package com.mscg.kotlinNative.maven.plugin;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.codehaus.mojo.exec.ExecMojo;
import org.codehaus.plexus.util.DirectoryScanner;

@Mojo( name = "exec", threadSafe = true, requiresDependencyResolution = ResolutionScope.TEST )
public class KotlinNativeMojo extends ExecMojo {

  /**
   * List of fileset patterns to specify source files to include in compilation.
   */
  @Parameter
  private String[] includes;

  /**
   * List of fileset patterns to specify source files to exclude from compilation.
   */
  @Parameter
  private String[] excludes;

  /**
   * The folder in which the native executable will be placed. Defaults to <code>${project.build.directory}</code>
   */
  @Parameter( property = "kotlin.compiledFolder", defaultValue = "${project.build.directory}" )
  private String compiledFolder;

  /**
   * The base name of native executable that will be generated. Defaults to <code>${project.artifactId}</code>
   */
  @Parameter( property = "kotlin.compiledFile", defaultValue = "${project.artifactId}" )
  private String compiledFile;

  /**
   * A switch to enable optimizations in the generated executables. Defaults to <code>false</code>
   */
  @Parameter( property = "kotlin.optimizations", defaultValue = "false" )
  private boolean optimizations;

  private FieldAccessor<String> _executable;

  private FieldAccessor<List<?>> _arguments;

  private void initFields() throws MojoExecutionException {
    try {
      Field field = ExecMojo.class.getDeclaredField("executable");
      field.setAccessible(true);
      _executable = new FieldAccessor<>(this, field);
    }
    catch (Exception e) {
      getLog().error(e);
      throw new MojoExecutionException("Failed to get executable property", e);
    }

    try {
      Field field = ExecMojo.class.getDeclaredField("arguments");
      field.setAccessible(true);
      _arguments = new FieldAccessor<>(this, field);
    }
    catch (Exception e) {
      getLog().error(e);
      throw new MojoExecutionException("Failed to get arguments property", e);
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public void execute() throws MojoExecutionException {
    try {
      initFields();

      if (_executable.get() == null) {
        getLog().info("Setting default value for kotlinc-native compiler");
        _executable.set("kotlinc-native");
      }

      List<Object> _arguments = (List<Object>) this._arguments.get();
      if (_arguments == null) {
        _arguments = new ArrayList<>();
        this._arguments.set(_arguments);
      }

      if (optimizations) {
        _arguments.add("-opt");
      }

      File outputFile = new File(compiledFolder, this.compiledFile);
      outputFile = outputFile.getCanonicalFile();
      String path = safeFileName(outputFile.getPath());
      getLog().info("Compiling application into basename " + path);
      _arguments.add("-o");
      _arguments.add(path);

      getLog().debug("Listing sources with include filters " + Optional.ofNullable(includes).map(Arrays::toString).orElse("<empty>") +
          " and exclude filters " + Optional.ofNullable(excludes).map(Arrays::toString).orElse("<empty>"));
      Stream<String> sourceRootsStream = project.getCompileSourceRoots().stream();
      List<String> sources = sourceRootsStream
        .map(File::new)
        .filter(File::exists)
        .flatMap(basedir -> {
          DirectoryScanner scanner = new DirectoryScanner();
          scanner.setIncludes(includes);
          scanner.setExcludes(excludes);
          scanner.setBasedir(basedir);
          scanner.scan();

          return Arrays.stream(scanner.getIncludedFiles())
              .map(file -> new File(basedir, file));
        })
        .map(t -> {
          try {
            return t.getCanonicalPath();
          }
          catch (IOException e) {
            throw new RuntimeException(e);
          }
        })
        .map(this::safeFileName)
        .collect(Collectors.toList());
      getLog().info("Adding " + sources.size() + " files to command line arguments");
      _arguments.addAll(sources);

    }
    catch (MojoExecutionException e) {
      throw e;
    }
    catch (RuntimeException e) {
      Throwable cause = e.getCause();
      throw new MojoExecutionException("An error occurred while initializing plugin configuration: " + e.getMessage(), cause);
    }
    catch (Exception e) {
      throw new MojoExecutionException("An error occurred while initializing plugin configuration: " + e.getMessage(), e);
    }

    super.execute();
  }

  private String safeFileName(String baseFileName) {
    if (baseFileName.contains(" ")) {
      return "\"" + baseFileName + "\"";
    }

    return baseFileName;
  }

  private static class FieldAccessor<T> implements Supplier<T>, Consumer<T> {

    private Object target;
    private Field field;

    public FieldAccessor(Object target, Field field) {
      this.target = target;
      this.field = field;
    }

    @Override
    @SuppressWarnings("unchecked")
    public T get() {
      try {
        return (T) field.get(target);
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    public void set(T t) {
      try {
        field.set(target, t);
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public void accept(T t) {
      set(t);
    }
  }

}
