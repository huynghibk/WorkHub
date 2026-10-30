package com.workhub.server

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.EqOp
import org.jetbrains.exposed.sql.IsNullOp
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.booleanLiteral

/** Max length for shared sequential string IDs ("1", "2", ...). */
const val ID_LEN = 50

/** Compact URL / path storage (prefer object storage + URL over BLOB/TEXT). */
const val URL_LEN = 500

/**
 * System columns every production entity table should have.
 * - created_at / updated_at: audit
 * - is_active: soft disable without deleting
 * - deleted_at: soft delete (null = alive)
 */
fun Table.sysCreatedAt() = long("created_at")
fun Table.sysUpdatedAt() = long("updated_at").default(0L)
fun Table.sysIsActive() = bool("is_active").default(true)
fun Table.sysDeletedAt() = long("deleted_at").nullable()

/** Predicate: row is not soft-deleted. */
fun alive(deletedAt: Column<Long?>): Op<Boolean> = IsNullOp(deletedAt)

/** Predicate: active and not deleted. */
fun usable(isActive: Column<Boolean>, deletedAt: Column<Long?>): Op<Boolean> =
    EqOp(isActive, booleanLiteral(true)) and IsNullOp(deletedAt)
