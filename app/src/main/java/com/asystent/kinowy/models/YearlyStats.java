package com.asystent.kinowy.models;

public class YearlyStats {
    private final String year;
    private final double totalHours;
    private final int closingShifts;
    private final int openingShifts;
    private final int longestMarathon;
    private final String topCoworker;
    private final String topCategory;
    private final String bestMonth;
    private final double bestMonthAmount;
    private final String worstMonth;
    private final double worstMonthAmount;

    public YearlyStats(String year, double totalHours, int closingShifts, int openingShifts,
                       int longestMarathon, String topCoworker, String topCategory,
                       String bestMonth, double bestMonthAmount, String worstMonth, double worstMonthAmount) {
        this.year = year;
        this.totalHours = totalHours;
        this.closingShifts = closingShifts;
        this.openingShifts = openingShifts;
        this.longestMarathon = longestMarathon;
        this.topCoworker = topCoworker;
        this.topCategory = topCategory;
        this.bestMonth = bestMonth;
        this.bestMonthAmount = bestMonthAmount;
        this.worstMonth = worstMonth;
        this.worstMonthAmount = worstMonthAmount;
    }

    public String getYear() { return year; }
    public double getTotalHours() { return totalHours; }
    public int getClosingShifts() { return closingShifts; }
    public int getOpeningShifts() { return openingShifts; }
    public int getLongestMarathon() { return longestMarathon; }
    public String getTopCoworker() { return topCoworker; }
    public String getTopCategory() { return topCategory; }
    public String getBestMonth() { return bestMonth; }
    public double getBestMonthAmount() { return bestMonthAmount; }
    public String getWorstMonth() { return worstMonth; }
    public double getWorstMonthAmount() { return worstMonthAmount; }
}
