var rocks = { name: "Rocks",
    sex: "Male",
    pets: [ { "name": "Pangpang", type: "Dog" }, { name: "Yaya", type: "Duck" } ]
}
var all = [ rocks,
    { name: "Duoduo", "sex": "Famale", pets: [ { name: "Xiaohua", type: "Cat" } ] }
]

for (var i in all) {
    with (all[i]) {
        println("$name is $sex, has $pets.length pet(s).")
        for (var j = 0; j < pets.length; j++) {
            println(" - $pets[j].name is a $pets[j].type")
        }
    }
}
delete rocks.sex;
delete rocks.pets[0];
for (var i in all) {
    with (all[i]) {
        println("$name is $sex, has $pets.length pet(s).")
        for (var j = 0; j < pets.length; j++) {
            println(" - $pets[j].name is a $pets[j].type")
        }
    }
}
