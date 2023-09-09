package uppex.semantics

import Configurations.Products

case class Configurations(products: Products, annotations: Annotations, xmlBlocks: Annotations, featModel: Option[FeatureModel.FM]):
  def withProduct(p:String): Configurations =
    Configurations(products+(""->products.getOrElse(p,Map())),annotations,xmlBlocks,featModel)

  def update(key: String, updateFn: Any => Any): Configurations = key match {
    case "products" =>
      this.copy(products = updateFn(products).asInstanceOf[Products])
    case "annotations" =>
      this.copy(annotations = updateFn(annotations).asInstanceOf[Annotations])
    case "xmlBlocks" =>
      this.copy(xmlBlocks = updateFn(xmlBlocks).asInstanceOf[Annotations])
    case "featModel" =>
      this.copy(featModel = updateFn(featModel).asInstanceOf[Option[FeatureModel.FM]])
    case _ =>
      throw new IllegalArgumentException(s"Unknown key: $key")
  }

object Configurations:
  type FProd = Map[String,Any] // set of features (names) with a value
  type Products = Map[String,FProd]
  def apply():Configurations = Configurations(Map(),Annotations(Map()),Annotations(Map()),None)


