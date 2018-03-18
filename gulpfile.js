const gulp = require("gulp");
const babel = require("gulp-babel");

gulp.task("build", () => gulp.src("./javascript/**/*.js")
	.pipe(babel())
	.pipe(gulp.dest("./public/javascripts")));