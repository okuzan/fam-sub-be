package com.almonium.famsubbe.converter

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@Converter(autoApply = true)
class YearMonthConverter : AttributeConverter<YearMonth, String> {
    
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM")
    
    override fun convertToDatabaseColumn(attribute: YearMonth?): String? {
        return attribute?.format(formatter)
    }
    
    override fun convertToEntityAttribute(dbData: String?): YearMonth? {
        return dbData?.let { YearMonth.parse(it, formatter) }
    }
}
