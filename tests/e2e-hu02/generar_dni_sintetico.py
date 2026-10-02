"""Genera las fotos sinteticas del DNI para la prueba de punta a punta de HU-02.

No hay DNI reales en el repositorio (ni debe haberlos: son datos personales). Estas
imagenes imitan la geometria de un DNI peruano (formato ISO/IEC 7810 ID-1) sobre una
mesa, con los rotulos reales del anverso y un MRZ TD1 valido en el reverso, de modo que
pasan por la deteccion, la validacion de calidad y el OCR reales de kyc-service.

Titular sintetico (debe coincidir con lo que se declara al registrarse):
  DNI 44556677 · ANA LUCIA · QUISPE MAMANI · nacida 15/05/1990 · F · emitido 20/08/2021

Uso, desde la raiz del repositorio (necesita Pillow y una fuente monoespaciada):
  PYTHONPATH=services/ayni-kyc-service python3 tests/e2e-hu02/generar_dni_sintetico.py \\
      [carpeta_de_salida]

Por defecto escribe en tests/e2e-hu02/imagenes/:
  dni_anverso.jpg  anverso valido
  dni_reverso.jpg  reverso valido con MRZ (digitos verificadores correctos)
  dni_borroso.jpg  el anverso desenfocado        -> motivo DESENFOQUE
  no_es_dni.jpg    un objeto cuadrado, no un DNI -> motivo NO_ES_DNI
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

from src.infrastructure.vision.mrz_td1 import _checksum, parsear_mrz

FUENTES_MONO = (
    "/usr/share/fonts/google-noto-vf/NotoSansMono[wght].ttf",
    "/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf",
    "/usr/share/fonts/dejavu-sans-mono-fonts/DejaVuSansMono.ttf",
)

# Tarjeta gris claro, no blanca: con mas del 5 % de pixeles por encima de 240 el
# validador la rechazaria por REFLEJO.
FONDO, TARJETA, TINTA = (62, 54, 46), (208, 214, 220), (28, 32, 40)
ANCHO, ALTO, ANCHO_TARJETA, ALTO_TARJETA = 1400, 1000, 860, 542  # 860/542 = 1.587
X0, Y0 = (ANCHO - ANCHO_TARJETA) // 2, (ALTO - ALTO_TARJETA) // 2


def _fuente(tamano: int) -> ImageFont.FreeTypeFont:
    for ruta in FUENTES_MONO:
        if Path(ruta).exists():
            return ImageFont.truetype(ruta, tamano)
    raise SystemExit("No se encontro una fuente monoespaciada; anade su ruta a FUENTES_MONO.")


def _mrz() -> tuple[str, str, str]:
    numero = "44556677<"
    linea1 = f"I<PER{numero}{_checksum(numero)}" + "<" * 15
    nacimiento, caducidad, opcional = "900515", "300101", "<" * 11
    compuesto = linea1[5:30] + nacimiento + str(_checksum(nacimiento)) + caducidad + str(_checksum(caducidad)) + opcional
    linea2 = f"{nacimiento}{_checksum(nacimiento)}F{caducidad}{_checksum(caducidad)}PER{opcional}{_checksum(compuesto)}"
    linea3 = "QUISPE<MAMANI<<ANA<LUCIA".ljust(30, "<")
    datos = parsear_mrz(linea1, linea2, linea3)
    assert datos is not None and datos.todos_los_checksums_validos
    return linea1, linea2, linea3


def _lienzo() -> tuple[Image.Image, ImageDraw.ImageDraw]:
    imagen = Image.new("RGB", (ANCHO, ALTO), FONDO)
    dibujo = ImageDraw.Draw(imagen)
    dibujo.rectangle([X0, Y0, X0 + ANCHO_TARJETA, Y0 + ALTO_TARJETA], fill=TARJETA)
    return imagen, dibujo


def generar(salida: Path) -> None:
    salida.mkdir(parents=True, exist_ok=True)

    anverso, d = _lienzo()
    d.text((X0 + 30, Y0 + 20), "REPUBLICA DEL PERU", font=_fuente(34), fill=TINTA)
    d.text((X0 + 30, Y0 + 62), "DNI 44556677", font=_fuente(30), fill=TINTA)
    d.rectangle([X0 + 30, Y0 + 120, X0 + 230, Y0 + 370], fill=(150, 156, 165))  # la foto
    filas = [("Primer Apellido", "QUISPE"), ("Segundo Apellido", "MAMANI"), ("Pre Nombres", "ANA LUCIA"),
             ("Fecha de Nacimiento", "15 05 1990"), ("Sexo", "F"), ("Fecha de Emision", "20 08 2021")]
    y = Y0 + 112
    for rotulo, valor in filas:
        d.text((X0 + 270, y), rotulo, font=_fuente(22), fill=(70, 76, 86))
        d.text((X0 + 270, y + 24), valor, font=_fuente(30), fill=TINTA)
        y += 68
    anverso.save(salida / "dni_anverso.jpg", quality=95)
    anverso.filter(ImageFilter.GaussianBlur(14)).save(salida / "dni_borroso.jpg", quality=95)

    reverso, d = _lienzo()
    d.text((X0 + 30, Y0 + 30), "RENIEC  -  LIMA", font=_fuente(30), fill=TINTA)
    # Este rotulo es a proposito: 29 mayusculas sin espacios, rompia encontrar_lineas_mrz.
    d.text((X0 + 30, Y0 + 80), "Departamento LIMA  Provincia LIMA", font=_fuente(24), fill=TINTA)
    for i, linea in enumerate(_mrz()):
        d.text((X0 + 40, Y0 + 330 + i * 62), linea, font=_fuente(44), fill=TINTA)
    reverso.save(salida / "dni_reverso.jpg", quality=95)

    otro = Image.new("RGB", (ANCHO, ALTO), FONDO)
    d = ImageDraw.Draw(otro)
    d.rectangle([400, 200, 1000, 800], fill=TARJETA)
    d.text((470, 460), "NO SOY UN DNI", font=_fuente(48), fill=TINTA)
    otro.save(salida / "no_es_dni.jpg", quality=95)

    print(f"Imagenes generadas en {salida}")


if __name__ == "__main__":
    generar(Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).parent / "imagenes")
