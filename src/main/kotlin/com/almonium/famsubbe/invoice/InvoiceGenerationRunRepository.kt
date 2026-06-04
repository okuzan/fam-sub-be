package com.almonium.famsubbe.invoice

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface InvoiceGenerationRunRepository : JpaRepository<InvoiceGenerationRun, UUID>
