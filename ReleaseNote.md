# Proline-Core Release Note

## Version 2.5.0 (Snapshot)

* Isobaric Quantitation bug fixes / improvement
  * read peptideMatches PIF Values : may have multiple PSM and not only one per Spectrum
  * fixes #26451: Quant TMT PSMCount error: all PSM/Reporter ions were considered even if Abundance was null/0 or if reporter ion was invalidated
* consider MQReporterIon selectionLevel + count only if Ab>0
* Add function to retrieve `PtmDefinition`s for a unimodID
* Add more tests for `PtmSitesClusterer`
* Import DiaNN feature
  * Add DiaNNQuantifier to link with the java "Import DiaNN module"
  * Add `PeptideMatchDiaNNProperties` where q values map is stored
* [Dev] Update dependencies (profi-commons, mzdb) and update code for it
* [Dev] Refactor POM files: replace Scala version suffix with classifier

## Version 2.4.0

* Minor change do to refactoring  
  * `PeakelDBHelper` in mzdb
  * in `profi-commons`: new module profi-proteomics-java for mgf io...


## Version 2.3.x

* Add "MgfBoost" in Peaklist Software
* Copy properties from reference Identification Summary to Quantitation Summary

## Version 2.3.0

see https://www.profiproteomics.fr/proline/proline-support/ 