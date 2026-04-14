with open("app/src/main/kotlin/io/github/landwarderer/futon/tracker/domain/TrackingRepository.kt", "r") as f:
    lines = f.readlines()

new_lines = []
skip = False
in_head = False
for line in lines:
    if line.startswith("<<<<<<< HEAD"):
        in_head = True
        skip = False
    elif line.startswith("======="):
        in_head = False
        skip = True
    elif line.startswith(">>>>>>> origin/"):
        skip = False
    elif not skip:
        new_lines.append(line)

with open("app/src/main/kotlin/io/github/landwarderer/futon/tracker/domain/TrackingRepository.kt", "w") as f:
    f.writelines(new_lines)
