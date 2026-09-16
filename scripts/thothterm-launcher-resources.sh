#! /bin/sh

set -e

cd `dirname $0`

gimp_convert() {
  # launcher icon size = 32 dp * ( dpi / 160 ) * 1.5
  size=`expr $dpi \* 3 / 10`

  # start gimp with script-fu batch-interpreter
  gimp -i --batch-interpreter=plug-in-script-fu-eval -b - << EOF
(define (convert xcf_file png_file)
  (let*
    (
      (img (car (gimp-file-load RUN-NONINTERACTIVE xcf_file xcf_file)))
      (layer (car (gimp-image-merge-visible-layers img CLIP-TO-IMAGE)))
    )

    (gimp-image-scale img $size $size)

    (gimp-file-save RUN-NONINTERACTIVE img layer png_file png_file)
    (gimp-image-delete img)
  )
)

(convert "$XCFFILE" "../term/src/main/res/$PNGFILE")

(gimp-quit 1)
EOF
}


for T in n r ; do

case $T in
n) XCFFILE=../docs/thothterm-launcher-icon.xcf;;
r) XCFFILE=../docs/thothterm-launcher-round_icon.xcf;;
*) exit 99;;
esac

for MODE in l m h xh xxh xxxh ; do
  case "$MODE" in
  l)	dpi=120;;
  m)	dpi=160;;
  h)	dpi=240;;
  xh)	dpi=320;;
  xxh)	dpi=480;;
  xxxh)	dpi=640;;
  *)	dpi=160;;
  esac

  qualifier=
  test -z "$MODE" || qualifier=-"$MODE"dpi

  case $T in
  n) PNGFILE=mipmap"$qualifier"/ic_launcher.png;;
  r) PNGFILE=mipmap"$qualifier"/ic_launcher_round.png;;
  esac
  echo creating .../$PNGFILE ... >&2

  gimp_convert
done
done
