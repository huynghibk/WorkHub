package com.workhub.server

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.IsNullOp
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.Table

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
fun Table.sysCreatedAt(): Column<Long> = long("created_at")
fun Table.sysUpdatedAt(): Column<Long> = long("updated_at").default(0L)
fun Table.sysIsActive(): Column<Boolean> = bool("is_active").default(true)
fun Table.sysDeletedAt(): Column<Long?> = long("deleted_at").nullable()

/** Predicate: row is not soft-deleted (deleted_at IS NULL). */
fun alive(deletedAt: Column<Long?>): Op<Boolean> = IsNullOp(deletedAt)

/**
 * Row is usable for queries.
 * Only soft-delete is enforced here (deleted_at). is_active is kept on the row for
 * future disable flows; filtering on it previously hid legacy NULL/false rows after ALTER.
 */
@Suppress("UNUSED_PARAMETER")
fun usable(isActive: Column<Boolean>, deletedAt: Column<Long?>): Op<Boolean> =
    IsNullOp(deletedAt)
