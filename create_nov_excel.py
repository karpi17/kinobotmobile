import openpyxl

wb = openpyxl.Workbook()
ws = wb.active
ws.title = "Grafik"

# Row 0: Positions & Month
ws.cell(row=1, column=1, value="November 160")
ws.cell(row=1, column=3, value="Manager")
ws.cell(row=1, column=5, value="Deputy Manager")
ws.cell(row=1, column=7, value="Deputy Manager")
ws.cell(row=1, column=9, value="Team Leader")
ws.cell(row=1, column=11, value="Team Leader")
ws.cell(row=1, column=13, value="Team Leader")
ws.cell(row=1, column=15, value="Team Leader")

# Row 1: Names
ws.cell(row=2, column=1, value="Dzień")
ws.cell(row=2, column=3, value="Kasia")
ws.cell(row=2, column=5, value="Zosia")
ws.cell(row=2, column=7, value="Kacper")
ws.cell(row=2, column=9, value="Dominik")
ws.cell(row=2, column=11, value="Alan")
ws.cell(row=2, column=13, value="Kacper")
ws.cell(row=2, column=15, value="Sara")

# Days data: (day_num, day_name, kasia, kasia_h, zosia, zosia_h, kacper_dm, k_dm_h, dom, dom_h, alan, alan_h, kacper_tl, k_tl_h, sara, sara_h)
days_data = [
    (1, "Sunday", "off", None, "vacation", 8, "close od 14", 11, "off", None, "close", 8, "off", None, "off", None),
    (2, "Monday", "close", 12, "vacation", 8, "WZ (Bydgoszcz+M)", 10, "off", None, "off", None, "open", 8, "close", 8),
    (3, "Tuesday", "off", None, "vacation", 8, "close", 8, "close", 8, "open", 8, "off", None, "off", None),
    (4, "Wednesday", "close", 12, "vacation", 8, "off", None, "close", 8, "off", None, "off", None, "open", 8),
    (5, "Thursday", "off", None, "vacation", 8, "close", 8, "off", None, "close", 8, "open", 8, "off", None),
    # summary 1
    ("S1", 24, 32, 37, 16, 24, 16, 16),
    (6, "Friday", "off", None, "close", 8, "off", None, "open", 8, "off", None, "close od 18", 7, "off", None),
    (7, "Saturday", "close", 8, "off", None, "open", 8, "close", 8, "off", None, "12–20", 8, "off", None),
    (8, "Sunday", "off", None, "close", 8, "open", 8, "12--20", 8, "off", None, "close", 8, "off", None),
    (9, "Monday", "close", 12, "off", None, "off", None, "off", None, "open", 8, "off", None, "close", 8),
    (10, "Tuesday", "close", 12, "WZ(Bydgoszcz)", 8, "off", None, "close", 8, "open", 8, "off", None, "off", None),
    (11, "Wednesday", "close", 12, "off", None, "open", 7, "close", 8, "off", None, "off", None, "12--20", 8),
    (12, "Thursday", "off", None, "close", 8, "off", None, "off", None, "close", 8, "open", 8, "off", None),
    # summary 2
    ("S2", 44, 32, 23, 40, 24, 31, 16),
    (13, "Friday", "off", None, "off", None, "close od 19", 6, "close", 8, "off", None, "off", None, "open", 8),
    (14, "Saturday", "open", 12, "close", 8, "off", None, "close", 8, "off", None, "12–20", 8, "off", None),
    (15, "Sunday", "open", 8, "close", 8, "off", None, "off", None, "off", None, "close", 8, "off", None),
    (16, "Monday", "off", None, "close", 8, "off", None, "off", None, "open", 8, "off", None, "close", 8),
    (17, "Tuesday", "close", 12, "WZ(szkolenia)", 12, "off", None, "close", 8, "open", 8, "off", None, "off", None),
    (18, "Wednesday", "off", None, "off", None, "close", 8, "close", 8, "off", None, "off", None, "open", 8),
    (19, "Thursday", "close", 12, "off", None, "off", None, "off", None, "off", None, "close", 8, "open", 8),
    # summary 3
    ("S3", 44, 36, 14, 32, 16, 24, 32),
    (20, "Friday", "off", None, "open", 12, "close od 19", 6, "close", 8, "off", None, "off", None, "off", None),
    (21, "Saturday", "close", 8, "open", 8, "off", None, "off", None, "off", None, "close", 8, "off", None),
    (22, "Sunday", "off", None, "open", 8, "close", 8, "close", 8, "off", None, "12–20", 8, "off", None),
    (23, "Monday", "off", None, "close", 8, "off", None, "off", None, "close", 8, "open", 8, "off", None),
    (24, "Tuesday", "close", 12, "off", None, "off", None, "close", 8, "off", None, "off", None, "open", 8),
    (25, "Wednesday", "off", None, "off", None, "close", 8, "off", None, "open", 8, "off", None, "off", None),
    (26, "Thursday", "W-wa", 8, "off", None, "close", 8, "off", None, "close", 8, "open", 8, "off", None),
    # summary 4
    ("S4", 28, 36, 30, 24, 24, 32, 16),
    (27, "Friday", "W-wa", 8, "close", 8, "off", None, "close", 8, "off", None, "off", None, "open", 8),
    (28, "Saturday", "off", None, "close", 8, "open", 8, "off", None, "close", 8, "off", None, "off", None),
    (29, "Sunday", "off", None, "close", 8, "open", 8, "close", 8, "12--20", 8, "off", None, "off", None),
    (30, "Monday", "close", 12, "off", None, "off", None, "off", None, "off", None, "close", 8, "open", 8),
    # summary 5
    ("S5", 20, 24, 16, 16, 16, 8, 16),
]

current_r = 3
for row_info in days_data:
    if str(row_info[0]).startswith("S"):
        # Summary row
        ws.cell(row=current_r, column=4, value=row_info[1])
        ws.cell(row=current_r, column=6, value=row_info[2])
        ws.cell(row=current_r, column=8, value=row_info[3])
        ws.cell(row=current_r, column=10, value=row_info[4])
        ws.cell(row=current_r, column=12, value=row_info[5])
        ws.cell(row=current_r, column=14, value=row_info[6])
        ws.cell(row=current_r, column=16, value=row_info[7])
    else:
        day_num = row_info[0]
        day_name = row_info[1]
        ws.cell(row=current_r, column=1, value=day_num)
        ws.cell(row=current_r, column=2, value=day_name)
        
        # Kasia
        ws.cell(row=current_r, column=3, value=row_info[2])
        if row_info[3] is not None: ws.cell(row=current_r, column=4, value=row_info[3])
        
        # Zosia
        ws.cell(row=current_r, column=5, value=row_info[4])
        if row_info[5] is not None: ws.cell(row=current_r, column=6, value=row_info[5])
        
        # Kacper DM
        ws.cell(row=current_r, column=7, value=row_info[6])
        if row_info[7] is not None: ws.cell(row=current_r, column=8, value=row_info[7])
        
        # Dominik TL
        ws.cell(row=current_r, column=9, value=row_info[8])
        if row_info[9] is not None: ws.cell(row=current_r, column=10, value=row_info[9])
        
        # Alan TL
        ws.cell(row=current_r, column=11, value=row_info[10])
        if row_info[11] is not None: ws.cell(row=current_r, column=12, value=row_info[11])
        
        # Kacper TL
        ws.cell(row=current_r, column=13, value=row_info[12])
        if row_info[13] is not None: ws.cell(row=current_r, column=14, value=row_info[13])
        
        # Sara TL
        ws.cell(row=current_r, column=15, value=row_info[14])
        if row_info[15] is not None: ws.cell(row=current_r, column=16, value=row_info[15])
    current_r += 1

wb.save("grafik_listopad.xlsx")
print("Saved grafik_listopad.xlsx successfully!")
