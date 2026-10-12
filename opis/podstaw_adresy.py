"""Podstawia adresy wgranych obrazków do opisu.

1. Wgraj obrazki z folderu obrazki/ na CurseForge (zakładka Images projektu).
2. Wpisz adres każdego z nich w adresy.txt, w linii z jego nazwą (nazwa.png = https://...).
3. python podstaw_adresy.py   ->   opis_curseforge.html, gotowy do wklejenia w edytor CurseForge (tryb źródła).

Obrazek bez adresu zostaje przy ścieżce lokalnej i jest wypisany na końcu.
"""
import os
import re

HERE = os.path.dirname(os.path.abspath(__file__))


def main():
    adresy = {}
    with open(os.path.join(HERE, "adresy.txt"), encoding="utf-8") as fh:
        for line in fh:
            if "=" not in line or line.lstrip().startswith("#"):
                continue
            name, url = (p.strip() for p in line.split("=", 1))
            if url:
                adresy[name] = url
    with open(os.path.join(HERE, "opis.html"), encoding="utf-8") as fh:
        html = fh.read()
    brak = []

    def podstaw(m):
        name = m.group(1)
        if name in adresy:
            return 'src="%s"' % adresy[name]
        brak.append(name)
        return m.group(0)
    html = re.sub(r'src="obrazki/([^"]+)"', podstaw, html)
    with open(os.path.join(HERE, "opis_curseforge.html"), "w", encoding="utf-8") as fh:
        fh.write(html)
    print("zapisano opis_curseforge.html")
    if brak:
        print("bez adresu (%d): %s" % (len(brak), ", ".join(sorted(set(brak)))))


if __name__ == "__main__":
    main()
