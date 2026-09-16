#! /bin/sh

set -e

cd `dirname $0`

gimp_convert() {
  # launcher icon size = 32 dp * ( dpi / 160 ) * 1.5
  size=`expr $dpi \* 3 / 10`
  xsize=`expr $dpi / 2`
  off=`expr \( $xsize - $size \) / 2`

  # start gimp with script-fu batch-interpreter
  gimp -i --batch-interpreter=plug-in-script-fu-eval -b - << EOF
(define (convert xcf_file png_file)
  (let*
    (
      (img (car (gimp-file-load RUN-NONINTERACTIVE xcf_file xcf_file)))
      (layer (car (gimp-image-merge-visible-layers img CLIP-TO-IMAGE)))
    )

    (gimp-image-scale img $size $size)
    (gimp-image-resize img $xsize $xsize $off $off)
    (gimp-layer-resize layer $xsize $xsize $off $off)

    (gimp-file-save RUN-NONINTERACTIVE img layer png_file png_file)
    (gimp-image-delete img)
  )
)

(convert "$XCFFILE" "../term/src/main/res/$PNGFILE")

(gimp-quit 1)
EOF
}


for T in f m ; do

case $T in
f) XCFFILE=../docs/thothterm-launcher-icon.xcf;;
m) XCFFILE=../docs/thothterm-launcher-bw_icon.xcf;;
*) exit 99;;
esac

for MODE in l m h xh xxh xxxh ; do
  case "$MODE" in
  l)	dpi=120; continue;; # unused
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
  f) PNGFILE=mipmap"$qualifier"/ic_launcher_foreground.png;;
  m) PNGFILE=mipmap"$qualifier"/ic_launcher_monochrome.png;;
  esac
  mkdir -p ../term/src/main/res/mipmap"$qualifier" || :
  echo creating .../$PNGFILE ... >&2

  gimp_convert
done
done
