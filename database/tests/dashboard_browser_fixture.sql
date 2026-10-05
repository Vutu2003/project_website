-- Used only by test-v3-browser.sh --dashboard in its disposable database.
BEGIN;
DO $$
DECLARE actor bigint; company bigint; own bigint; other bigint; campaign bigint; pending bigint; device record; coverage bigint; item bigint; attempt bigint;
BEGIN
 SELECT id INTO actor FROM user_account WHERE username='vtyt';
 SELECT id INTO company FROM service_provider WHERE active ORDER BY id LIMIT 1;
 SELECT department_id INTO own FROM user_account WHERE username='khoa_noi';
 SELECT id INTO other FROM department WHERE active AND id<>own ORDER BY id LIMIT 1;
 INSERT INTO maintenance_plan(title,period_start,period_end,status,created_by_user_id)
 VALUES ('Chrome — bàn giao theo phạm vi',CURRENT_DATE,CURRENT_DATE,'IN_PROGRESS',actor) RETURNING id INTO campaign;
 FOR device IN SELECT DISTINCT ON (department_id) id,department_id FROM equipment WHERE department_id IN (own,other) ORDER BY department_id,equipment_code LOOP
  SELECT id INTO coverage FROM maintenance_coverage WHERE equipment_id=device.id ORDER BY id DESC LIMIT 1;
  INSERT INTO maintenance_plan_item(plan_id,equipment_id,department_id_at_plan,status,assigned_provider_id,assignment_route,coverage_id)
  VALUES(campaign,device.id,device.department_id,'AWAITING_HANDOVER',company,'UNDER_CONTRACT',coverage) RETURNING id INTO item;
  INSERT INTO maintenance_execution(plan_item_id,provider_id,started_by_user_id,attempt_no,started_at,ended_at,result_note)
  VALUES(item,company,actor,1,now()-interval '1 hour',now()-interval '30 minutes','Đã kiểm tra kỹ thuật') RETURNING id INTO attempt;
  INSERT INTO acceptance_record(execution_id,acceptance_type,result,observed_at,conclusion,recorded_by_user_id)
  VALUES(attempt,'TECHNICAL_ACCEPTANCE','PASS',now()-interval '15 minutes','Thiết bị đạt và chờ bàn giao',actor);
 END LOOP;
 INSERT INTO maintenance_plan(title,period_start,period_end,status,created_by_user_id)
 VALUES('Chrome — kế hoạch chờ BGĐ',CURRENT_DATE,CURRENT_DATE,'SUBMITTED',actor) RETURNING id INTO pending;
 INSERT INTO approval_request(plan_id,request_type,status,created_by_user_id,submitted_at)
 VALUES(pending,'PLAN_APPROVAL','PENDING',actor,now());
 INSERT INTO user_notification(user_account_id,notification_type,title,message,target_url)
 VALUES (actor,'MAINTENANCE_STARTED','Chrome — tiến độ bảo trì','Kế hoạch đang được thực hiện.','/maintenance-progress/plans/'||campaign),
        (actor,'REPORT_SHARED','Chrome — cập nhật báo cáo','Kiểm tra các kết quả bảo trì.','/maintenance-progress/plans/'||campaign),
        (actor,'PLAN_APPROVED','Chrome — kế hoạch đã duyệt','Theo dõi các thiết bị trong kế hoạch.','/maintenance-progress/plans/'||campaign);
END $$;
COMMIT;
