function foo()
{
    var i = 0;
    while (i < 2)
    {
        while (true)
        {
            return;
        }
        i++;
    }
    return i;
}

console.log(foo());