package org.scalafmt

object FilesUtil {
  def listFiles(path: String): Vector[String] = {
    def listFilesIter(s: java.io.File): Iterator[String] = {
      val (dirs, files) = Option(s.listFiles()).toIterator
        .flatMap(_.toIterator)
        .partition(_.isDirectory)
      files.map(_.getPath) ++ dirs.flatMap(listFilesIter)
    }
    for {
      f0 <- Option(listFilesIter(new java.io.File(path))).toVector
      filename <- f0
    } yield filename
  }

}
