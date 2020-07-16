function exec(user) {
    let executor = context.getExecutor("hello-world/welcome");
    // let ex2 = context2.getExecutor();
    return executor.execute(user);
    // return executor.execute(reverse(user));
}
