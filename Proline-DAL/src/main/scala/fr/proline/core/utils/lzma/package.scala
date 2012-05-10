package fr.proline.core.utils

package object lzma {

  object EasyLzma {
    
    import java.io.{ByteArrayOutputStream}
    import org.tukaani.xz.{LZMA2Options,XZOutputStream}
    
    case class LzmaOptions( var dictSize: Int, var mode: Int, var niceLen: Int, var depth: Int,
                            var lc: Int, var lp: Int, var pb: Int, var mf: Int )
                            
    var defaultOptions = new LzmaOptions( 
                               dictSize = 1 << 16, // 65536
                               mode = LZMA2Options.MODE_FAST,
                               niceLen = 128,
                               depth = 4,
                               lc = LZMA2Options.LC_DEFAULT,
                               lp = LZMA2Options.LP_DEFAULT,
                               pb = LZMA2Options.PB_DEFAULT,
                               mf = LZMA2Options.MF_HC4
                               )
    
    /*
     -a{N}:  set compression mode 0 = fast, 1 = normal
              default: 1 (normal)
    
      d{N}:   Sets Dictionary size - [0, 30], default: 23 (8MB)
              The maximum value for dictionary size is 1 GB = 2^30 bytes.
              Dictionary size is calculated as DictionarySize = 2^N bytes. 
              For decompressing file compressed by LZMA method with dictionary 
              size D = 2^N you need about D bytes of memory (RAM).
    
      -fb{N}: set number of fast bytes - [5, 273], default: 128
              Usually big number gives a little bit better compression ratio 
              and slower compression process.
    
      -lc{N}: set number of literal context bits - [0, 8], default: 3
              Sometimes lc=4 gives gain for big files.
    
      -lp{N}: set number of literal pos bits - [0, 4], default: 0
              lp switch is intended for periodical data when period is 
              equal 2^N. For example, for 32-bit (4 bytes) 
              periodical data you can use lp=2. Often it's better to set lc0, 
              if you change lp switch.
    
      -pb{N}: set number of pos bits - [0, 4], default: 2
              pb switch is intended for periodical data 
              when period is equal 2^N.
    
      -mf{MF_ID}: set Match Finder. Default: bt4. 
                  Algorithms from hc* group doesn't provide good compression 
                  ratio, but they often works pretty fast in combination with 
                  fast mode (-a0).
    
                  Memory requirements depend from dictionary size 
                  (parameter "d" in table below). 
    
                   MF_ID     Memory                   Description
    
                    bt2    d *  9.5 + 4MB  Binary Tree with 2 bytes hashing.
                    bt3    d * 11.5 + 4MB  Binary Tree with 3 bytes hashing.
                    bt4    d * 11.5 + 4MB  Binary Tree with 4 bytes hashing.
                    hc4    d *  7.5 + 4MB  Hash Chain with 4 bytes hashing.
    
      -eos:   write End Of Stream marker. By default LZMA doesn't write 
              eos marker, since LZMA decoder knows uncompressed size 
              stored in .lzma file header.
     */
    def compress( data: Array[Byte] ): Array[Byte] = {
      
      val options = this.defaultOptions
      val encoderOptions = new LZMA2Options(
                                options.dictSize, 
                                options.lc,
                                options.lp,
                                options.pb,
                                options.mode,
                                options.niceLen,
                                options.mf,
                                options.depth
                                )
      
      val baOS = new ByteArrayOutputStream();
      val encoder = new XZOutputStream(baOS, encoderOptions)
      encoder.write( data )
      encoder.finish()
      
      baOS.toByteArray

    }
    
  }
  
}