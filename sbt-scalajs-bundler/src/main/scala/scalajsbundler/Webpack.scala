package scalajsbundler

import sbt._

object Webpack {

  import JS.syntax._

  /**
    * Writes the webpack configuration file
    *
    * @param emitSourceMaps Whether source maps is enabled at all
    * @param webpackEntries Module entries (name, file.js)
    * @param targetDir Directory to write the file into
    * @param log Logger
    * @return The written file
    */
  def writeConfigFile(
    emitSourceMaps: Boolean,
    webpackEntries: Seq[(String, File)],
    targetDir: File,
    log: Logger
  ): File = {
    // Create scalajs.webpack.config.js
    val webpackConfigFile = targetDir / "scalajs.webpack.config.js" // TODO discriminate filename according to sjs stage
    val webpackConfigContent =
      JS.ref("module") `.` "exports" := JS.obj(Seq(
        "entry" -> JS.obj(webpackEntries.map { case (key, file) =>
          key -> JS.str(file.absolutePath) }: _*
        ),
        "output" -> JS.obj(
          "path" -> JS.str(targetDir.absolutePath),
          "filename" -> JS.str(bundleName("[name]"))
        )
      ) ++ (
        if (emitSourceMaps) {
          Seq(
            "devtool" -> JS.str("source-map"),
            "module" -> JS.obj(
              "preLoaders" -> JS.arr(
                JS.obj(
                  "test" -> JS.regex("\\.js$"),
                  "loader" -> JS.str("source-map-loader")
                )
              )
            )
          )
        } else Nil
        ): _*)
    log.debug("Writing 'scalajs.webpack.config.js'")
    IO.write(webpackConfigFile, webpackConfigContent.show)

    webpackConfigFile
  }

  /**
    * Run webpack to bundle the application.
    *
    * @param generatedWebpackConfigFile Webpack config file generated by scalajs-bundler
    * @param customWebpackConfigFile User supplied config file
    * @param entries Module entries
    * @param targetDir Target directory (and working directory for Nodejs)
    * @param log Logger
    * @return The generated bundles
    */
  def bundle(
    generatedWebpackConfigFile: File,
    customWebpackConfigFile: Option[File],
    entries: Seq[(String, File)],
    targetDir: File,
    log: Logger
  ): Seq[File] = {

    val configFile =
      customWebpackConfigFile match {
        case Some(file) =>
          val configFileCopy = targetDir / file.name
          IO.copyFile(file, configFileCopy)
          configFileCopy
        case None =>
          generatedWebpackConfigFile
      }

    log.info("Bundling the application with its NPM dependencies")
    Webpack.run("--config", configFile.absolutePath)(targetDir, log)

    val bundles =
      entries.map { case (key, _) =>
        // TODO Support custom webpack config file (the output may be overridden by users)
        targetDir / Webpack.bundleName(key)
      }
    bundles
  }

  /** Filename of the generated bundle, given its module entry name */
  def bundleName(entry: String): String = s"$entry-bundle.js"

  /**
    * Runs the webpack command.
    *
    * @param args Arguments to pass to the webpack command
    * @param workingDir Working directory in which the Nodejs will be run (where there is the `node_modules` subdirectory)
    * @param log Logger
    */
  def run(args: String*)(workingDir: File, log: Logger): Unit = {
    val webpackBin = workingDir / "node_modules" / "webpack" / "bin" / "webpack"
    val cmd = s"node ${webpackBin.absolutePath} ${args.mkString(" ")}"
    Commands.run(cmd, workingDir, log)
    ()
  }

}
