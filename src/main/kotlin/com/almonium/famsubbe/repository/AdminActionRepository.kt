package com.almonium.famsubbe.repository

import com.almonium.famsubbe.entity.AdminAction
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface AdminActionRepository : JpaRepository<AdminAction, UUID>