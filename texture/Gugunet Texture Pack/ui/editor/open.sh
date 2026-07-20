#!/bin/bash
cd "$(dirname "$0")"
if command -v xdg-open &> /dev/null; then
  xdg-open index.html
elif command -v open &> /dev/null; then
  open index.html
else
  echo "Abra index.html no navegador:"
  echo "$(pwd)/index.html"
fi
