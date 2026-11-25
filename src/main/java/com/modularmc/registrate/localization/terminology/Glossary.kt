package com.modularmc.registrate.localization.terminology

import com.modularmc.registrate.localization.terminology.Terminology.GlossaryTerm
import com.modularmc.registrate.localization.terminology.Terminology.TermRelation

/**
 * 向模组的 [Terminology] 提供术语查询，可由不同形式的存储实现。
 * 在模组初始化时注册，数据生成期间应保持查询结果稳定。
 * 同一模组的各词汇表共用术语标识，一个标识只能对应一条定义。
 */
interface Glossary {
	/**
	 * 按稳定标识查询术语。
	 *
	 * @param id 查询的术语标识
	 * @return 对应的术语，当前词汇表没有该术语时返回 `null`
	 */
	fun find(id: String): GlossaryTerm?

	/**
	 * 按完整的源语言词条或别名查询，保留不同领域和文本类型下的各个词义。
	 *
	 * @param normalizedSource 按与语言区域无关的规则转为小写的查询词
	 * @return 匹配的术语，没有匹配项时返回空集合
	 */
	fun findBySource(normalizedSource: String): Iterable<GlossaryTerm>

	/**
	 * 查询指定关系直接关联的术语。查询同义关系时同时返回正向和反向关联。
	 * 关系数据由实现类提供，数据生成期间应保持查询结果稳定。
	 *
	 * @param id 待查询的术语标识
	 * @param relation 要匹配的关系类型
	 * @return 与该术语直接关联的术语
	 */
	fun findRelated(id: String, relation: TermRelation): Iterable<GlossaryTerm>
}
