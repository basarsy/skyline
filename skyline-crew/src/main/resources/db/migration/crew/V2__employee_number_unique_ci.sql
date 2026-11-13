-- Employee numbers are unique regardless of case ("E100" and "e100" are the same employee).
-- V1 already enforces exact-match uniqueness; this closes the case-variant gap.
CREATE UNIQUE INDEX uq_crew_members_employee_number_ci ON crew_members (LOWER(employee_number));
