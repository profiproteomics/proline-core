package fr.proline.core.utils

/**
 * @author David Bouyssie
 *
 */
package object io {
  
  import scala.io.{BufferedSource,Source}
  
  class RichBufferedSource(self: BufferedSource) {
    def eachLine( fn: String => Unit ) {
      val lines = self.getLines()
      
      for(line <- lines) {
        fn( line )
      }
    }
    
    def eachLine( separator: String, fn: String => Unit ) { 
      val blockIter = new LineIterator(self, separator)
      
      while( blockIter.hasNext ) {
        val block = blockIter.next()
        fn( block )
      }
    }
  }
  implicit def bufSrcToRichBufSrc(bufSrc: BufferedSource) = new RichBufferedSource(bufSrc)

  /** Source: http://asoftsea.tumblr.com/post/529750770/a-transitional-suitcase-for-source */
  import scala.io.BufferedSource
  class LineIterator(bufferedSrc: BufferedSource, separator: String) extends Iterator[String] {
    require(separator.length < 3, "Line separator may be 1 or 2 characters only.")
    
    private[this] val iter: BufferedIterator[Char] = bufferedSrc.buffered
    
    private[this] val isNewline: Char => Boolean =
      separator.length match {
        case 1 => _ == separator(0)
        case 2 => {
          _ == separator(0) && iter.hasNext && {
            val res = iter.head == separator(1) // peek ahead
            if (res) { iter.next } // incr iter
              res
            }
          }
        }
    
    private[this] val builder = new StringBuilder
  
    private def buildingLine() = iter.next match {
      case nl if(isNewline(nl)) => false
      case ch =>  { 
        builder append ch
        true
      }
    }
  
    def hasNext = iter.hasNext
    def next = {
      builder.clear
      while (hasNext && buildingLine()) {}
      builder.toString
    }
  }
  
  /*object Transitioning {
    import scala.io.Source
    class TransitionalSource(src: Source) {    
      def lines = new LineIterator(src.buffered, compat.Platform.EOL)
    }
    implicit def src2transitionalSrc(src: Source) = new TransitionalSource(src)
  }*/
}