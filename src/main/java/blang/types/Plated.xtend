package blang.types

import blang.inits.ConstructorArg
import blang.inits.Creator
import blang.inits.DesignatedConstructor
import blang.inits.GlobalArg
import blang.inits.InitService
import blang.inits.parsing.QualifiedName
import blang.io.DataSource
import blang.types.internals.ColumnName
import blang.types.internals.HashPlated
import blang.types.internals.PlatedSlice
import blang.types.internals.Query
import blang.types.internals.SimpleParser
import com.google.inject.TypeLiteral
import java.lang.reflect.ParameterizedType
import java.util.Map.Entry
import java.util.Optional
import blang.io.internals.GlobalDataSourceStore

/**
 * A random variable or parameter of type T enclosed in one or more Plates.
 */
interface Plated<T> extends Iterable<Entry<Query, T>> {
  
  /**
   * The random variable or parameter indexed by the provided indices.
   */
  def T get(Index<?> ... indices) 
  
  
  def Plated<T> slice(Index<?> ... indices) {
    return new PlatedSlice(this, Query::build(indices)) 
  }
  
  /**
   * Parser automatically called by the inits infrastructure. 
   * 
   * If a DataSource is available, the values will be parsed from the strings in that DataSource, otherwise,
   * all will be parsed via the string NA:SYMBOL.
   * 
   * A DataSource is available if:
   * a) either a GlobalDataSource has been defined in the model, or a DataSource is provided for this Plated (the latter has priority if both present).
   * b) the DataSource has a column with name corresponding to the name given to the declared Plated variable.
   */
  @DesignatedConstructor
  def public static <T> Plated<T> parse(
    @ConstructorArg("name") Optional<ColumnName> name,
    @ConstructorArg("dataSource") DataSource dataSource,
    @GlobalArg GlobalDataSourceStore globalDataSourceStore,
    @InitService QualifiedName qualifiedName,
    @InitService TypeLiteral<T> typeLiteral,
    @InitService Creator creator 
  ) {
    val ColumnName columnName = name.orElse(new ColumnName(qualifiedName.simpleName()))
    // data source
    var DataSource scopedDataSource = DataSource::scopedDataSource(dataSource, globalDataSourceStore)
    if (scopedDataSource.present && !scopedDataSource.columnNames.contains(columnName)) {
      scopedDataSource = DataSource::empty
    }
    // parser
    val TypeLiteral<T> typeArgument = 
      TypeLiteral.get((typeLiteral.type as ParameterizedType).actualTypeArguments.get(0))
      as TypeLiteral<T>
    return new HashPlated(columnName, scopedDataSource, new SimpleParser(creator, typeArgument))
  }
  
  
}