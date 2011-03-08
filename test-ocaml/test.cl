let f = fun a -> a;;
(*§(1, f 66)*)
(*let (a, (b, c)) = §(1, §(3, let f = fun a -> a*a in f 4))  in a + b + c*)
(*let (a, b, c, d, e) = §(f 1,f 2,f 3,f 4,f 5) in a+b+c+d+e*)
(*let (a, (b, (c, d))) = §(f 1, §(f 2, §(f 3, f 4))) f in a+b+c+d*)
(*let ((b, c), a) = §(§(2, 3), 1) in a+c+b*)

let rec g_ = function
	  0 -> 0
	| 1 -> 1
	| n -> let (a, b) = §(g_ (n-1), g_ (n-2)) in a+b;;

let rec g = function
	  0 -> 0
	| 1 -> 1
	| n -> let (a, b) = (g (n-1), g (n-2)) in a+b;;

(*let (a,b,c,d,e,f) = §(g 25, g 25, g 25, g 25, g 25, g 25) in a+b+c+d+e+f*)

let a = g_ 35 in a
