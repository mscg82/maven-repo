package com.mscg.kotlinNative.maven.plugin

import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.plugins.annotations.ResolutionScope
import org.codehaus.mojo.exec.ExecMojo
import java.lang.reflect.Field
import java.util.function.Consumer
import java.util.function.Supplier
import java.io.File
import org.codehaus.plexus.util.DirectoryScanner

@Mojo(name = "exec", threadSafe = true, requiresDependencyResolution = ResolutionScope.TEST)
class KotlinNativeMojo : ExecMojo() {
    /**
     * List of fileset patterns to specify source files to include in compilation.
     */
    @Parameter
    private var includes: Array<String>? = null

    /**
     * List of fileset patterns to specify source files to exclude from compilation.
     */
    @Parameter
    private var excludes: Array<String>? = null
    
    /**
     * The source directories containing the sources to be compiled.
     */
    @Parameter
    private var sourceDirs: List<String>? = null
        get() {
            val dirs: MutableList<String> = mutableListOf()
            val size = (field?.size ?: 0);
            if (size != 0) {
                dirs += field!!
            }
            else {
                dirs += project.compileSourceRoots.map { it as String }
            }
            dirs += (additionalSourceDirs ?: listOf())
            return dirs
        }
    
    /**
     * Additional source directories containing the sources to be compiled.
     */
    @Parameter
    private var additionalSourceDirs: List<String>? = null

    /**
     * The folder in which the native executable will be placed. Defaults to <code>${project.build.directory}</code>
     */
    @Parameter(property = "kotlin.compiledFolder", defaultValue = "\${project.build.directory}")
    private lateinit var compiledFolder: String;

    /**
     * The base name of native executable that will be generated. Defaults to <code>${project.artifactId}</code>
     */
    @Parameter(property = "kotlin.compiledFile", defaultValue = "\${project.artifactId}")
    private lateinit var compiledFile: String;

    /**
     * A switch to enable optimizations in the generated executables. Defaults to <code>false</code>
     */
    @Parameter(property = "kotlin.optimizations", defaultValue = "false")
    private var optimizations: Boolean = false;
        
    /**
     * Enable multiplatform features
     */
    @Parameter(property = "kotlin.multiPlatform", defaultValue = "false")
    private var multiPlatform: Boolean = false

    private lateinit var _executable: FieldAccessor<String>;

    private lateinit var _arguments: FieldAccessor<MutableList<*>>;

    @Throws(MojoExecutionException::class)
    private fun initFields() {
        try {
            val field = ExecMojo::class.java.getDeclaredField("executable");
            field.setAccessible(true);
            _executable = FieldAccessor(this, field);
        } catch (e: Exception) {
            log.error(e);
            throw MojoExecutionException("Failed to get executable property", e);
        }

        try {
            val field = ExecMojo::class.java.getDeclaredField("arguments");
            field.setAccessible(true);
            _arguments = FieldAccessor(this, field);
        } catch (e: Exception) {
            log.error(e);
            throw MojoExecutionException("Failed to get arguments property", e);
        }
    }
    
    @Suppress("UNCHECKED_CAST")
    @Throws(MojoExecutionException::class)
    override fun execute() {
        try {
            initFields()

            if (_executable.get() == null) {
                log.info("Setting default value for kotlinc-native compiler");
                _executable.set("kotlinc-native");
            }

            var _arguments: MutableList<Any?>? = this._arguments.get() as MutableList<Any?>?
            if (_arguments == null) {
                _arguments = mutableListOf()
                this._arguments.set(_arguments);
            }

            with(_arguments) {
                if (optimizations) {
                    add("-opt");
                }
                
                if (multiPlatform) {
                    add("-Xmulti-platform")
                }

                val targetFolder = File(compiledFolder)
                if (!targetFolder.exists()) {
                    log.info("Creating target folder ${targetFolder.canonicalPath}")
                    if (!targetFolder.mkdirs()) {
                        throw MojoExecutionException("Failed to create target folder ${targetFolder.canonicalPath} for native executable")
                    }
                }
                if (targetFolder.exists() && !targetFolder.isDirectory) {
                    throw MojoExecutionException("Path ${targetFolder.canonicalPath} is not a folder that can contain the native executable")
                }
                val outputFile = File(compiledFolder, compiledFile).getCanonicalFile();
                val path = outputFile.getPath().safeFileName();
                log.info("Compiling application into basename $path");
                add("-o");
                add(path);
                
                if (log.isDebugEnabled) {
                  log.debug("Listing sources with include filters ${includes?.contentToString() ?: "<empty>"} and exclude filters ${excludes?.contentToString() ?: "<empty>"}")                    
                }
                val sources: List<String> = (sourceDirs ?: listOf()).asSequence()
                    .map { File(it) }
                    .filter { it.exists() }
                    .flatMap { sourceBasedir ->
                        with(DirectoryScanner()) {
                            setIncludes(this@KotlinNativeMojo.includes)
                            setExcludes(this@KotlinNativeMojo.excludes)
                            basedir = sourceBasedir
                            scan()
                            
                            includedFiles.asSequence().map { File(sourceBasedir, it) }
                        }
                    }
                    .map { it.canonicalPath.safeFileName() }
                    .toList()
                
                if (log.isDebugEnabled) {
                    log.debug("Source files:");
                    sources.forEach { log.debug("  $it") }
                }
                log.info("Adding ${sources.size} files to command line arguments");
                addAll(sources);
            }

        } catch (e: MojoExecutionException) {
            throw e;
        } catch (e: RuntimeException) {
            val cause: Throwable? = e.cause;
            throw MojoExecutionException("An error occurred while initializing plugin configuration: ${e.message}", cause);
        } catch (e: Exception) {
            throw MojoExecutionException("An error occurred while initializing plugin configuration: ${e.message}", e);
        }

        super.execute()
    }


    private fun String.safeFileName(): String =
        if (this.contains(" ")) {
            """"$this"""";
        }
        else {
            this;
        }
}

private class FieldAccessor<T>(val target: Any, val field: Field) : Supplier<T?>, Consumer<T?> {

    @Suppress("UNCHECKED_CAST")
    override fun get(): T? {
        return field.get(target) as T;
    }

    fun set(t: T?) {
        field.set(target, t);
    }

    override fun accept(t: T?) {
        set(t)
    }

}