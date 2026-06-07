package com.weekmenu.data

object Categories {
    val RECIPE = listOf("carne", "vegetariano", "air-fryer", "seitan", "peixe", "sopa", "outros")
    val INGREDIENT = listOf("carne", "vegetais", "mercearia", "congelados", "laticinios", "fruta", "outros")
    val GROCERY = listOf("meat", "vegetables", "pantry", "frozen", "dairy", "fruit", "other")

    val GROCERY_LABELS = mapOf(
        "meat" to "Meat",
        "vegetables" to "Vegetables",
        "pantry" to "Pantry",
        "frozen" to "Frozen",
        "dairy" to "Dairy",
        "fruit" to "Fruit",
        "other" to "Other"
    )
}
