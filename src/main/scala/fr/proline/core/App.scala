package fr.proline.core
import fr.proline.core.om.factory.PeptideLoader



/**
 * @author ${user.name}
 * 
 * 
 */
object App {
  
  def foo(x : Array[String]) = x.foldLeft("")((a,b) => a + b)
  
  def main(args : Array[String]) {
    println("Hello World!")
    println("concat arguments = " + foo(args))
    
    import net.noerd.prequel.IsolationLevels
    import net.noerd.prequel.DatabaseConfig
    import net.noerd.prequel.SQLFormatterImplicits._
    import net.noerd.prequel.ResultSetRowImplicits._
    
    implicit val database = DatabaseConfig(
        driver = "org.sqlite.JDBC",
        jdbcURL = "jdbc:sqlite:D:/prosper/data/msi-db.sqlite",
        isolationLevel = IsolationLevels.Serializable
    )
    
    /* val nbEnzymes = database.transaction { tx => 
          tx.selectLong( "select count(*) from enzyme")
      }
    
    println( nbEnzymes )*/
    
    /*simport fr.proline.core.om.factory.MsQueryLoader
    val msqLoader = new MsQueryLoader( database )
    
    val msQueries = msqLoader.getMsQueries( Array(1,2,3,4) )
    println( msQueries.length )*/
    
    /*import fr.proline.core.om.factory.PeptideLoader
    
    val pepLoader = new PeptideLoader(psDb = database)
    
    //println( pepLoader.ptmSpecificityMap.values.size )
    //println( pepLoader.getPeptidePtmRecordMap( Array(27) ).size )
    //println( pepLoader.ptmDefinitionMap.size )
    //println( pepLoader.ptmDefinitionMap.get(1).get.ptmEvidences(0).composition )
    
    val peptides = pepLoader.getPeptides( 20 to 10000 toArray )
    println(peptides(0).ptms(0).monoMass )
    println( peptides.length )*/
    
    import fr.proline.core.om.factory.PeptideMatchLoader
    val pepMatchLoader = new PeptideMatchLoader(msiDb = database, psDb = database)
    val pepMatches = pepMatchLoader.getPeptideMatches(rsIds = Array(1,2,3,4))
    println( pepMatches.length )
  }

}
