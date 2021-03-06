package org.scalafmt

import scala.collection.mutable
import scala.meta._
import scala.meta.tokens.Token

class ScalaFmt(style: ScalaStyle) extends ScalaFmtLogger {

  /**
    * Penalty to kill the current path.
    */
  val KILL = 1000

  /**
    * Pretty-prints Scala code.
    */
  def format(code: String): String = {
    val source = code.parse[Source]
    val toks = FormatToken.formatTokens(source.tokens)
    val path = shortestPath(source, toks)
    reconstructPath(toks, path)
  }

  /**
    * Returns formatted output from FormatTokens and Splits.
    */
  private def reconstructPath(toks: Array[FormatToken],
                              splits: Vector[Split]): String = {
    require(toks.length == splits.length)
    val sb = new StringBuilder()
    var indentation = 0
    toks.zip(splits).foreach {
      case (tok, split) =>
        sb.append(tok.left.code)
        indentation += split.indent
        val ws = split match {
          case _: Space =>
            sb.append(" ")
          case _: Newline =>
            sb.append("\n" + " " * indentation)
          case _ =>
            // Nothing
        }
    }
    sb.toString()
  }


  /**
    * Runs Dijstra's shortest path algorithm to find lowest penalty split.
    */
  private def shortestPath(source: Source,
                           splitTokens: Array[FormatToken]): Vector[Split] = {
    val formatter = new Formatter(style, getOwners(source))
    val Q = new mutable.PriorityQueue[State]()
    var explored = 0
    var result = Vector.empty[Split]
    Q += State.start
    while (Q.nonEmpty) {
      val curr = Q.dequeue()
      if (curr.path.length == splitTokens.length) {
        result = curr.path
        Q.dequeueAll
      }
      else {
        explored += 1
        if (explored % 100000 == 0)
          println(explored)
        val splitToken = splitTokens(curr.path.length)
        val splits = (curr.strategy orElse formatter.GetSplits)(splitToken)
        splits.foreach { split =>
          Q.enqueue(next(curr, split, splitToken))
        }
      }
    }
    result
  } ensuring(_.length == splitTokens.length,
    "Unable to reach the last token.")

  /**
    * Calculates next State given split at tok.
    *
    * - Accumulates cost and strategies
    * - Calculates column-width overflow penalty
    */
  private def next(state: State,
                   split: Split,
                   tok: FormatToken): State = {
    val newIndent = state.indentation + split.indent
    val newColumn =
      if (split.isInstanceOf[Newline]) newIndent
      else state.column + split.length + tok.right.code.length
    val overflowPenalty = if (newColumn < style.maxColumn) 0 else KILL
    val totalCost = state.cost + split.cost + overflowPenalty
    State(totalCost,
      // TODO(olafur) expire strategies, see #18.
      split.Strategy orElse state.strategy,
      state.path :+ split,
      newIndent, newColumn)
  }

  /**
    * Creates lookup table from token to its closest scala.meta tree.
    */
  private def getOwners(source: Source): Map[Token, Tree] = {
    val result = mutable.Map.empty[Token, Tree]
    def loop(x: Tree): Unit = {
      x.tokens
        .foreach { tok =>
          result += tok -> x
        }
      x.children.foreach(loop)
    }
    loop(source)
    result.toMap
  }


}
