package com.example.academyhub.models

data class DayTimetable(
    val day: String = "",
    val timetables: List<Timetable> = listOf()
)