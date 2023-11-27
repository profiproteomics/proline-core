package fr.proline.core.algo.lcms

object MozCalibrationSmoother {

  import alignment._

  def apply(method: MozCalibrationSmoothing.Value): IAlnSmoother = {
    method match {
      case MozCalibrationSmoothing.LANDMARK_RANGE => new LandmarkRangeSmoother()
      case MozCalibrationSmoothing.LOESS => new LoessSmoother()
      case MozCalibrationSmoothing.TIME_WINDOW => new TimeWindowSmoother()
      case MozCalibrationSmoothing.MEAN => throw new NotImplementedError() //To be defined
    }
  }

  def apply(methodName: String): IAlnSmoother = {

    val smoothingMethod = try {
      MozCalibrationSmoothing.withName(methodName.toUpperCase())
    } catch {
      case _: Throwable => throw new Exception("can't find an appropriate moz calibration smoother")
    }
    MozCalibrationSmoother(smoothingMethod)
  }
}
