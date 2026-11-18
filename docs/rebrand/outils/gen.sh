#!/bin/zsh
# usage: [PX=6] gen.sh <dir> <file.png> <style.txt> <what.txt> [ref.png]
# PX replaces @@PX@@ in the style (size of an art pixel in image pixels, for sprite sheets)
S=${0:A:h}; dir=$1; file=$2; style=$3; what=$4; ref=$5
h="$(<$S/consignes/header.txt)"; h="${h//@@FILE@@/$file}"
extra=""
[[ -n "$ref" ]] && extra=$'\n'"ATTACHED IMAGE: a finished scene of the same game. Match its pixel scale, palette, outline colour, lighting and rendering exactly."
st="$(<$style)"; st="${st//@@PX@@/${PX:-6}}"
prompt="$h$extra"$'\n\n'"$st"$'\n\n'"$(<$what)"
print -r -- "$prompt" > "$dir/${file%.png}.prompt.txt"
refs=(); [[ -n "$ref" ]] && refs=(-i "$ref")
cd "$dir"
command codex exec --skip-git-repo-check --ephemeral -s workspace-write -C "$dir" "${refs[@]}" -o "$dir/${file%.png}.last.txt" "$prompt" < /dev/null > "$dir/${file%.png}.log" 2>&1
[[ -f "$dir/$file" ]] && echo "OK $dir/$file" || { echo "FAIL $dir/$file"; tail -5 "$dir/${file%.png}.log"; }
