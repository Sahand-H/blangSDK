package blang.types.internals

import blang.io.DataSource
import java.util.Map
import java.util.Set
import blang.types.internals.Query.QueryType
import java.util.LinkedHashMap
import blang.types.Plate
import blang.types.Index
import briefj.BriefMaps
import org.eclipse.xtend.lib.annotations.Accessors
import com.rits.cloning.Immutable

/**
 * Utility to quickly access entries in DataSource.
 */
@Immutable 
class IndexedDataSource {
  
  val ColumnName columnName
  
  @Accessors(PUBLIC_GETTER)
  val DataSource dataSource
  
  val Map<QueryType, Map<Query, Set<String>>> cache = new LinkedHashMap
  
  new(ColumnName columnName, DataSource dataSource) {
    this.columnName = columnName
    this.dataSource = dataSource
  }
  
  def Set<String> getStrings(Query query) {
    val QueryType queryType = query.type
    if (!cache.containsKey(queryType)) {
      computeCache(queryType)
    }
    return cache.get(queryType).get(query)
  }
  
  def String getString(Query query) {
    val Set<String> strings = getStrings(query)
    if (strings === null) {
      return null
    }
    if (strings.size > 1) {
      throw new RuntimeException("More than one match for " + query)
    }
    return strings.iterator.next
  }
  
  def void computeCache(QueryType queryType) {
    val Map<Query, Set<String>> currentCache = new LinkedHashMap
    for (Map<ColumnName, String> line : dataSource.read) {
      val Query curQuery = Query.build
      for (Plate<?> curPlate : queryType.plates) {
        val Object parsed = curPlate.parse(line.get(curPlate.name))
        curQuery.indices.add(new Index(curPlate, parsed)) 
      }
      BriefMaps.getOrPutSet(currentCache, curQuery).add(line.get(columnName))
    }
    cache.put(queryType, currentCache)
  }
}