package com.nagarro.techmappoc.model

data class PassportData(
    val documentNumber: String,
    val surname: String,
    val givenNames: String,
    val nationality: String,
    val dateOfBirth: String,
    val sex: String,
    val expiryDate: String,
    val uid: ByteArray,
    val photoAvailable: Boolean
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PassportData

        if (documentNumber != other.documentNumber) return false
        if (surname != other.surname) return false
        if (givenNames != other.givenNames) return false
        if (nationality != other.nationality) return false
        if (dateOfBirth != other.dateOfBirth) return false
        if (sex != other.sex) return false
        if (expiryDate != other.expiryDate) return false
        if (!uid.contentEquals(other.uid)) return false
        if (photoAvailable != other.photoAvailable) return false

        return true
    }

    override fun hashCode(): Int {
        var result = documentNumber.hashCode()
        result = 31 * result + surname.hashCode()
        result = 31 * result + givenNames.hashCode()
        result = 31 * result + nationality.hashCode()
        result = 31 * result + dateOfBirth.hashCode()
        result = 31 * result + sex.hashCode()
        result = 31 * result + expiryDate.hashCode()
        result = 31 * result + uid.contentHashCode()
        result = 31 * result + photoAvailable.hashCode()
        return result
    }
}
