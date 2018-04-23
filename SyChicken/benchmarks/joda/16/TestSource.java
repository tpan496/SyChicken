public boolean test0() throws Throwable {
    org.joda.time.LocalDate start = new org.joda.time.LocalDate(2015, 11, 12);
    org.joda.time.LocalDate now = org.joda.time.LocalDate.now();
    return daysUntilNow(start) == org.joda.time.Days.daysBetween(start, now).getDays();
}

public boolean test1() throws Throwable {
    org.joda.time.LocalDate start = new org.joda.time.LocalDate(2014, 11, 12);
    org.joda.time.LocalDate now = org.joda.time.LocalDate.now();
    return daysUntilNow(start) == org.joda.time.Days.daysBetween(start, now).getDays();
}

public boolean test() throws Throwable {
    return test0() && test1();
}
